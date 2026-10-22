package com.vasilevyuv.auth.api

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.vasilevyuv.support.PostgresApiTest
import com.vasilevyuv.support.TEST_JWT_AUDIENCE
import com.vasilevyuv.support.TEST_JWT_ISSUER
import com.vasilevyuv.support.TEST_JWT_SECRET
import com.vasilevyuv.support.TEST_PASSWORD
import com.vasilevyuv.support.assertError
import com.vasilevyuv.support.login
import com.vasilevyuv.support.register
import com.vasilevyuv.support.token
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthRoutesTest : PostgresApiTest() {
    @Test
    fun `registration returns only the public user fields and normalizes login`() = apiTest {
        val response = client.register("  StuDent_1  ")
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(setOf("id", "login", "role"), Json.parseToJsonElement(response.bodyAsText()).jsonObject.keys)
        val user = Json.decodeFromString<UserResponse>(response.bodyAsText())
        assertTrue(user.id > 0)
        assertEquals("student_1", user.login)
        assertEquals("USER", user.role)
    }

    @Test
    fun `passwords are stored as salted bcrypt hashes with cost 12`() = apiTest {
        assertEquals(HttpStatusCode.Created, client.register("first").status)
        assertEquals(HttpStatusCode.Created, client.register("second").status)
        val hashes = withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT password_hash FROM users ORDER BY id").use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }
        assertEquals(2, hashes.size)
        assertNotEquals(hashes[0], hashes[1])
        for (hash in hashes) {
            assertTrue(hash.startsWith("\$2a\$12\$"))
            assertEquals(60, hash.length)
            assertFalse(hash.contains(TEST_PASSWORD))
            assertTrue(BCrypt.verifyer().verify(TEST_PASSWORD.toCharArray(), hash).verified)
            assertFalse(BCrypt.verifyer().verify("Wrong password".toCharArray(), hash).verified)
        }
    }

    @Test
    fun `duplicate normalized login returns 409 and leaves the original password unchanged`() = apiTest {
        client.register("Student")
        client.register(" STUDENT ", "Another password")
            .assertError(HttpStatusCode.Conflict, "LOGIN_ALREADY_TAKEN")
        assertEquals(HttpStatusCode.OK, client.login("student").status)
        client.login("student", "Another password").assertError(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
    }

    @Test
    fun `concurrent registrations cannot create duplicate users`() = apiTest {
        val responses = coroutineScope {
            listOf("Student", "STUDENT").map { login -> async { client.register(login) } }.awaitAll()
        }
        assertEquals(1, responses.count { it.status == HttpStatusCode.Created })
        responses.single { it.status != HttpStatusCode.Created }
            .assertError(HttpStatusCode.Conflict, "LOGIN_ALREADY_TAKEN")
        assertEquals(1, userCount())
    }

    @Test
    fun `login returns a signed expiring JWT with the registered user id`() = apiTest {
        val user = Json.decodeFromString<UserResponse>(client.register().bodyAsText())
        val response = client.login(" STUDENT ")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        val token = response.token()
        assertEquals("Bearer", token.tokenType)
        assertEquals(3600, token.expiresIn)
        val jwt = JWT.require(Algorithm.HMAC256(TEST_JWT_SECRET))
            .withIssuer(TEST_JWT_ISSUER).withAudience(TEST_JWT_AUDIENCE).build().verify(token.accessToken)
        assertEquals(user.id.toString(), jwt.getClaim("userId").asString())
        assertEquals(3_600_000, jwt.expiresAt.time - jwt.issuedAt.time)
        assertFalse(jwt.claims.containsKey("password"))
        assertFalse(jwt.claims.containsKey("passwordHash"))
    }

    @Test
    fun `unknown login and wrong password return the same generic 401`() = apiTest {
        client.register()
        val wrongPassword = client.login(password = "Wrong password")
        val unknownLogin = client.login("unknown")
        wrongPassword.assertError(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
        unknownLogin.assertError(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
        assertEquals(wrongPassword.bodyAsText(), unknownLogin.bodyAsText())
    }

    @Test
    fun `password is case sensitive and spaces are not trimmed`() = apiTest {
        val password = "  My password  "
        client.register(password = password)
        assertEquals(HttpStatusCode.OK, client.login(password = password).status)
        for (wrong in listOf(password.trim(), password.lowercase())) {
            client.login(password = wrong).assertError(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
        }
    }

    @Test
    fun `invalid logins and passwords return 400 without creating users`() = apiTest {
        for (login in listOf("", "  ", "ab", "a".repeat(33), "first last", "студент", "user@example.com", "Kelvin")) {
            client.register(login).assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            client.login(login).assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }
        for (password in listOf("", "short", "        ", "a".repeat(73), "я".repeat(37), "😀".repeat(4))) {
            client.register(password = password).assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            client.login(password = password).assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }
        assertEquals(0, userCount())
    }

    @Test
    fun `credential limits accept 8 characters and 72 UTF8 bytes without truncation`() = apiTest {
        val credentials = listOf(
            "abc" to "12345678",
            "a".repeat(32) to "a".repeat(72),
            "unicode" to "я".repeat(36),
            "emoji" to "😀".repeat(8),
        )
        for ((login, password) in credentials) {
            assertEquals(HttpStatusCode.Created, client.register(login, password).status)
            assertEquals(HttpStatusCode.OK, client.login(login, password).status)
            val wrongPassword = "x" + password.substring(password.offsetByCodePoints(0, 1))
            client.login(login, wrongPassword).assertError(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
        }
    }

    @Test
    fun `authentication endpoints reject malformed JSON and client supplied fields`() = apiTest {
        for (path in listOf("/auth/register", "/auth/login")) {
            val bodies = listOf(
                "{", "{}", "[]", "null", """{"login":"student"}""",
                """{"login":null,"password":"password"}""",
                """{"login":"student","password":42}""",
                """{"login":"student","password":"password","id":1}""",
                """{"login":"student","password":"password","role":"ADMIN"}""",
            )
            for (body in bodies) {
                client.post(path) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.assertError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
            }
        }
        assertEquals(0, userCount())
    }

    @Test
    fun `authentication endpoints require JSON`() = apiTest {
        for (path in listOf("/auth/register", "/auth/login")) {
            client.post(path) {
                contentType(ContentType.Text.Plain)
                setBody("student:password")
            }.assertError(HttpStatusCode.UnsupportedMediaType, "UNSUPPORTED_MEDIA_TYPE")
        }
    }

    @Test
    fun `registered users and issued tokens survive application restarts`() {
        lateinit var token: String
        apiTest {
            client.register()
            token = client.login().token().accessToken
        }
        apiTest(resetData = false) {
            assertEquals(HttpStatusCode.OK, client.login().status)
            client.register().assertError(HttpStatusCode.Conflict, "LOGIN_ALREADY_TAKEN")
            val response = client.post("/tasks") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody("""{"title":"After restart"}""")
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }
    }

    private fun userCount(): Int = withConnection { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM users").use { rows -> rows.next(); rows.getInt(1) }
        }
    }
}
