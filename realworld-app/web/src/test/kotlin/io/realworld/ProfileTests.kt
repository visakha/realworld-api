package io.realworld

import com.fasterxml.jackson.databind.ObjectMapper
import io.realworld.domain.common.Auth
import io.realworld.domain.common.Token
import io.realworld.domain.users.ValidUserRegistration
import io.realworld.persistence.UserRepository
import io.realworld.persistence.UserTbl
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.jdbc.JdbcTestUtils
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProfileTests {
  @Autowired
  lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @Autowired
  lateinit var auth: Auth

  @Autowired
  lateinit var userRepo: UserRepository

  @LocalServerPort
  var port: Int = 0

  @AfterEach
  fun deleteUser() {
    JdbcTestUtils.deleteFromTables(jdbcTemplate, UserTbl.table)
  }

  @Test
  fun `get profile`() {
    val user1 = validTestUserRegistration("foo", "foo@realworld.io")
    val user2 = validTestUserRegistration("bar", "bar@realworld.io")
    val user3 = validTestUserRegistration("baz", "baz@realworld.io")
    userRepo.create(user1).unsafeRunSync()
    userRepo.create(user2).unsafeRunSync()
    userRepo.create(user3).unsafeRunSync()

    userRepo.addFollower(user2.username, user1.username).unsafeRunSync()
    userRepo.addFollower(user3.username, user1.username).unsafeRunSync()

    get("/api/profiles/bar", user1.token)
      .then()
      .statusCode(200)
      .body("profile.username", equalTo("bar"))
      .body("profile.following", equalTo(true))

    get("/api/profiles/baz", user1.token)
      .then()
      .statusCode(200)
      .body("profile.username", equalTo("baz"))
      .body("profile.following", equalTo(true))

    get("/api/profiles/foo", user2.token)
      .then()
      .statusCode(200)
      .body("profile.username", equalTo("foo"))
      .body("profile.following", equalTo(false))
  }

  @Test
  fun `get profile without token`() {
    val user1 = validTestUserRegistration("foo", "foo@realworld.io")
    val user2 = validTestUserRegistration("bar", "bar@realworld.io")
    userRepo.create(user1).unsafeRunSync()
    userRepo.create(user2).unsafeRunSync()

    userRepo.addFollower(user1.username, user2.username).unsafeRunSync()

    get("/api/profiles/bar")
      .then()
      .statusCode(200)
      .body("profile.username", equalTo("bar"))
      .body("profile.following", equalTo(null))
  }

  @Test
  fun `follow`() {
    val user1 = validTestUserRegistration("foo", "foo@realworld.io")
    val user2 = validTestUserRegistration("bar", "bar@realworld.io")
    userRepo.create(user1).unsafeRunSync()
    userRepo.create(user2).unsafeRunSync()

    get("/api/profiles/bar", user1.token)
      .then()
      .statusCode(200)
      .body("profile.username", equalTo("bar"))
      .body("profile.following", equalTo(false))

    post("/api/profiles/bar/follow", null, user1.token)
      .then()
      .statusCode(200)
      .body("profile.username", equalTo("bar"))
      .body("profile.following", equalTo(true))
  }

  @Test
  fun `follow phantom`() {
    val user1 = validTestUserRegistration("foo", "foo@realworld.io")
    userRepo.create(user1).unsafeRunSync()

    post("/api/profiles/bar/follow", null, user1.token)
      .then()
      .statusCode(404)
  }

  @Test
  fun `follow already followed`() {
    val user1 = validTestUserRegistration("foo", "foo@realworld.io")
    val user2 = validTestUserRegistration("bar", "bar@realworld.io")
    userRepo.create(user1).unsafeRunSync()
    userRepo.create(user2).unsafeRunSync()
    userRepo.addFollower(user2.username, user1.username).unsafeRunSync()

    post("/api/profiles/bar/follow", null, user1.token)
      .then()
      .statusCode(200)
  }

  @Test
  fun `unfollow`() {
    val user1 = validTestUserRegistration("foo", "foo@realworld.io")
    val user2 = validTestUserRegistration("bar", "bar@realworld.io")
    userRepo.create(user1).unsafeRunSync()
    userRepo.create(user2).unsafeRunSync()

    post("/api/profiles/bar/follow", null, user1.token)
      .then()
      .statusCode(200)
      .body("profile.username", equalTo("bar"))
      .body("profile.following", equalTo(true))

    delete("/api/profiles/bar/follow", user1.token)
      .then()
      .statusCode(200)
      .body("profile.username", equalTo("bar"))
      .body("profile.following", equalTo(false))
  }

  @Test
  fun `unfollow phantom`() {
    val user1 = validTestUserRegistration("foo", "foo@realworld.io")
    userRepo.create(user1).unsafeRunSync()

    delete("/api/profiles/bar/follow", user1.token)
      .then()
      .statusCode(404)
  }

  @Test
  fun `unfollow not followed`() {
    val user1 = validTestUserRegistration("foo", "foo@realworld.io")
    val user2 = validTestUserRegistration("bar", "bar@realworld.io")
    userRepo.create(user1).unsafeRunSync()
    userRepo.create(user2).unsafeRunSync()

    delete("/api/profiles/bar/follow", user1.token)
      .then()
      .statusCode(200)
  }

  private fun get(path: String, token: String? = null) =
    RestAssured.given().baseUri("http://localhost:$port").token(token).get(path)

  private fun delete(path: String, token: String? = null) =
    RestAssured.given().baseUri("http://localhost:$port").token(token).delete(path)

  private fun post(path: String, body: Any?, token: String? = null) =
    RestAssured.given()
      .baseUri("http://localhost:$port")
      .token(token)
      .contentType(ContentType.JSON)
      .maybeBody(body)
      .post(path)

  private fun validTestUserRegistration(username: String, email: String): ValidUserRegistration {
    val id = UUID.randomUUID()
    return ValidUserRegistration(
      id = id,
      username = username,
      email = email,
      encryptedPassword = auth.encryptPassword("plain"),
      token = auth.createToken(Token(id))
    )
  }

  fun RequestSpecification.token(token: String?) =
    if (token != null) {
      this.header("Authorization", "Token $token")
    } else this

  fun RequestSpecification.maybeBody(body: Any?) =
    if (body != null) {
      this.body(body)
    } else this
}