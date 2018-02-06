package io.realworld

import com.fasterxml.jackson.annotation.JsonRootName
import io.realworld.domain.api.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.validation.Valid
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank

@JsonRootName("user")
data class Login(
  @field:Email
  @field:NotBlank
  val email: String,

  @field:NotBlank
  val password: String
)

@JsonRootName("user")
data class Registration(
  @field:NotBlank
  val username: String,

  @field:Email
  @field:NotBlank
  val email: String,

  @field:NotBlank
  val password: String
)

@JsonRootName("user")
data class UserUpdate(
  @field:Email
  val email: String? = null,

  val username: String? = null,
  val password: String? = null,
  val bio: String? = null,
  val image: String? = null
)

data class User(
  val email: String,
  val token: String,
  val username: String,
  val bio: String? = null,
  val image: String? = null
)
data class UserResponse(val user: User) {
  companion object {
    fun fromDto(dto: UserDto) = UserResponse(UserMappers.user.mapReverse(dto))
  }
}

@RestController
class UserController(
  private val registerUser: RegisterUser,
  private val loginUser: LoginUser
) {

  @GetMapping("/api/user")
  fun currentUser(user: UserDto) = ResponseEntity.ok().body(UserResponse.fromDto(user))

  @PostMapping("/api/users")
  fun register(@Valid @RequestBody registration: Registration): ResponseEntity<UserResponse> =
    registerUser(RegisterUserCommand(UserRegistration(
      username = registration.username,
      email = registration.email,
      password = registration.password
    )))
      .unsafeRunSync()
      .fold(
        {
          when (it) {
            is UserRegistrationValidationError.EmailAlreadyTaken ->
              throw FieldError("email", "already taken")
            is UserRegistrationValidationError.UsernameAlreadyTaken ->
              throw FieldError("username", "already taken")
          }
        },
        { ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.fromDto(it.user)) }
      )

  @PostMapping("/api/users/login")
  fun login(@Valid @RequestBody login: Login): ResponseEntity<UserResponse> =
    loginUser(LoginUserCommand(
      email = login.email,
      password = login.password
    ))
      .unsafeRunSync()
      .fold(
        { throw UnauthrorizedException() },
        { ResponseEntity.ok().body(UserResponse.fromDto(it.user)) })

  @PutMapping("/api/user")
  fun update(@Valid @RequestBody userUpdate: UserUpdate, user: UserDto): ResponseEntity<UserResponse> =
    ResponseEntity.ok().body(UserResponse.fromDto(user))
}

object UserMappers {
  val user = OrikaBeanMapper.FACTORY.getMapperFacade(User::class.javaObjectType, UserDto::class.javaObjectType)
}
