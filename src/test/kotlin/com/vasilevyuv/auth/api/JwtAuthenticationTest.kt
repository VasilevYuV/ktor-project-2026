package com.vasilevyuv.auth.api

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.vasilevyuv.support.PostgresApiTest
import com.vasilevyuv.support.TEST_JWT_AUDIENCE
import com.vasilevyuv.support.TEST_JWT_ISSUER
import com.vasilevyuv.support.TEST_JWT_SECRET
import com.vasilevyuv.support.assertError
import com.vasilevyuv.support.login
import com.vasilevyuv.support.register
import com.vasilevyuv.support.token
import com.vasilevyuv.tasks.api.TaskResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwtAuthenticationTest : PostgresApiTest() {
    @Test
    fun `all task writes require Bearer authentication and leave data unchanged on failure`() = apiTest {
        client.register()
        val token = client.login().token().accessToken
        val task = client.createTask(token)
        for (header in listOf(null, "", "Basic abc", "Bearer garbage", "Bearer")) {
            client.assertWritesRejected(task.id, header)
        }
        assertEquals(task, Json.decodeFromString<TaskResponse>(client.get("/tasks/${task.id}").bodyAsText()))
        assertEquals(listOf(task), Json.decodeFromString<List<TaskResponse>>(client.get("/tasks").bodyAsText()))
    }

    @Test
    fun `reads stay public even with an invalid Authorization header`() = apiTest {
        client.register()
        val task = client.createTask(client.login().token().accessToken)
        for (path in listOf("/", "/tasks", "/tasks/${task.id}")) {
            assertEquals(HttpStatusCode.OK, client.get(path).status)
            assertEquals(HttpStatusCode.OK, client.get(path) { bearerAuth("invalid") }.status)
        }
    }

    @Test
    fun `valid login token authorizes the complete write cycle`() = apiTest {
        client.register()
        val token = client.login().token().accessToken
        val task = client.createTask(token)
        val updated = client.put("/tasks/${task.id}") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Updated","description":"","status":"DONE"}""")
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals("DONE", Json.decodeFromString<TaskResponse>(updated.bodyAsText()).status)
        assertEquals(HttpStatusCode.NoContent, client.delete("/tasks/${task.id}") { bearerAuth(token) }.status)
    }

    @Test
    fun `wrong signature algorithm issuer audience and expired tokens are rejected`() = apiTest {
        client.register()
        val task = client.createTask(client.login().token().accessToken)
        val tokens = listOf(
            signedToken(algorithm = Algorithm.HMAC256("another-secret-at-least-32-bytes-long")),
            signedToken(algorithm = Algorithm.HMAC384(TEST_JWT_SECRET)),
            signedToken(algorithm = Algorithm.none()),
            signedToken { withIssuer("another-api") },
            signedToken { withAudience("another-client") },
            signedToken { withExpiresAt(Instant.now().minusSeconds(60)) },
            signedToken { withNotBefore(Instant.now().plusSeconds(3600)) },
            signedToken { withIssuedAt(Instant.now().plusSeconds(3600)) },
            signedToken().dropLast(8) + "tampered",
        )
        for (token in tokens) client.assertWritesRejected(task.id, "Bearer $token")
        assertEquals(task, Json.decodeFromString<TaskResponse>(client.get("/tasks/${task.id}").bodyAsText()))
    }

    @Test
    fun `missing or invalid expiry and user id claims cannot authorize writes`() = apiTest {
        client.register()
        val task = client.createTask(client.login().token().accessToken)
        val algorithm = Algorithm.HMAC256(TEST_JWT_SECRET)
        val tokens = listOf(
            JWT.create().withIssuer(TEST_JWT_ISSUER).withAudience(TEST_JWT_AUDIENCE)
                .withClaim("userId", "1").sign(algorithm),
            JWT.create().withIssuer(TEST_JWT_ISSUER).withAudience(TEST_JWT_AUDIENCE)
                .withExpiresAt(Instant.now().plusSeconds(3600)).sign(algorithm),
            signedToken { withNullClaim("exp") },
            signedToken { withClaim("exp", "never") },
            signedToken { withNullClaim("userId") },
            signedToken { withClaim("userId", true) },
            signedToken { withClaim("userId", 1.5) },
            signedToken { withClaim("userId", 1L) },
        ) + listOf("", "abc", "0", "-1", "999", "9223372036854775808").map { id ->
            signedToken { withClaim("userId", id) }
        }
        for (token in tokens) client.assertWritesRejected(task.id, "Bearer $token")
    }

    @Test
    fun `token stops working if its user no longer exists`() = apiTest {
        client.register()
        val token = client.login().token().accessToken
        val task = client.createTask(token)
        withConnection { connection -> connection.createStatement().use { it.executeUpdate("DELETE FROM users") } }
        client.assertWritesRejected(task.id, "Bearer $token")
        assertEquals(HttpStatusCode.OK, client.get("/tasks/${task.id}").status)
    }

    @Test
    fun `authentication runs before validating task ids and bodies`() = apiTest {
        client.post("/tasks") {
            contentType(ContentType.Application.Json)
            setBody("{")
        }.assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        client.put("/tasks/invalid").assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        client.delete("/tasks/invalid").assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
    }

    private suspend fun HttpClient.createTask(token: String): TaskResponse {
        val response = post("/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Protected task"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.assertWritesRejected(id: Long, authorization: String?) {
        val responses = listOf(
            post("/tasks") {
                authorization?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Unauthorized creation"}""")
            },
            put("/tasks/$id") {
                authorization?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Unauthorized update","description":"","status":"DONE"}""")
            },
            delete("/tasks/$id") { authorization?.let { header(HttpHeaders.Authorization, it) } },
        )
        for (response in responses) {
            response.assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
            assertTrue(response.headers[HttpHeaders.WWWAuthenticate]?.startsWith("Bearer ") == true)
        }
    }

    private fun signedToken(
        algorithm: Algorithm = Algorithm.HMAC256(TEST_JWT_SECRET),
        configure: JWTCreator.Builder.() -> Unit = {},
    ): String = JWT.create()
        .withIssuer(TEST_JWT_ISSUER)
        .withAudience(TEST_JWT_AUDIENCE)
        .withExpiresAt(Instant.now().plusSeconds(3600))
        .withClaim("userId", "1")
        .apply(configure)
        .sign(algorithm)
}
