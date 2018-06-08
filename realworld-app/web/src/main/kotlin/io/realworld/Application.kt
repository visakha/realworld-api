package io.realworld

import io.realworld.domain.api.User
import io.realworld.domain.core.Auth
import io.realworld.domain.spi.Settings
import io.realworld.domain.spi.UserRepository
import io.realworld.persistence.InMemoryUserRepository
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@SpringBootApplication
class Spring5Application {
  @Bean
  @ConfigurationProperties(prefix = "realworld")
  fun settings() = Settings()

  @Bean
  fun userArgumentResolver() = UserArgumentResolver(auth(), userRepository())

  @Bean
  fun auth() = Auth(settings().security)

  @Bean
  fun userRepository() = InMemoryUserRepository()
}

fun main(args: Array<String>) {
  SpringApplication.run(Spring5Application::class.java, *args)
}

class UserArgumentResolver(
  val auth: Auth,
  val userRepository: UserRepository
): HandlerMethodArgumentResolver {
  override fun supportsParameter(parameter: MethodParameter): Boolean =
    User::class.java.isAssignableFrom(parameter.parameterType)

  override fun resolveArgument(
    parameter: MethodParameter,
    bindingContext: BindingContext,
    exchange: ServerWebExchange
  ): Mono<Any> {
    val authorization: String? = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
    authorization?.apply {
      if (startsWith(TOKEN_PREFIX)) {
        return try {
          Mono.just(authenticate(substring(TOKEN_PREFIX.length)))
        } catch (t: Throwable) {
          throw UnauthrorizedException()
        }
      }
    }
    throw UnauthrorizedException()
  }

  private fun authenticate(tokenString: String): User {
    val token = auth.parse(tokenString)
    val user = userRepository.findByEmail(token.email)
    return when (user?.email) {
    // TODO check expiration
      token.email -> user.toDomain()
      else -> throw RuntimeException("Authentication required")
    }
  }

  companion object {
    private val TOKEN_PREFIX = "Token "
  }
}

class UnauthrorizedException : Throwable()
