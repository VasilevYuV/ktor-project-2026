package com.vasilevyuv.auth.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.vasilevyuv.support.PostgresApiTest
import com.vasilevyuv.support.TEST_ADMIN_LOGIN
import com.vasilevyuv.support.TEST_ADMIN_SETTINGS
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
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RoleAccessTest : PostgresApiTest() {
    @Test
    fun `me requires JWT and returns the current public user data for both roles`() = apiTest(settings = TEST_ADMIN_SETTINGS) {
        client.get("/auth/me").assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        client.get("/auth/me") { bearerAuth("invalid") }.assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        client.register()
        for ((login, role) in listOf("student" to "USER", TEST_ADMIN_LOGIN to "ADMIN")) {
            val token = client.login(login).token().accessToken
            val response = client.get("/auth/me") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertEquals(setOf("id", "login", "role"), Json.parseToJsonElement(response.bodyAsText()).jsonObject.keys)
            val user = Json.decodeFromString<UserResponse>(response.bodyAsText())
            assertEquals(login, user.login)
            assertEquals(role, user.role)
        }
    }

    @Test
    fun `user can create and update while only admin can delete`() = apiTest(settings = TEST_ADMIN_SETTINGS) {
        client.register()
        val userToken = client.login().token().accessToken
        val adminToken = client.login(TEST_ADMIN_LOGIN).token().accessToken
        val task = client.createTask(userToken)
        val path = "/tasks/${task.id}"
        val updated = client.put(path) {
            bearerAuth(userToken)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Updated by USER","description":"","status":"DONE"}""")
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        client.delete(path).assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        client.delete(path) { bearerAuth(userToken) }.assertError(HttpStatusCode.Forbidden, "FORBIDDEN")
        assertEquals(updated.bodyAsText(), client.get(path).bodyAsText())
        assertEquals(HttpStatusCode.NoContent, client.delete(path) { bearerAuth(adminToken) }.status)
        client.get(path).assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
    }

    @Test
    fun `role check runs before parsing ids and looking up tasks`() = apiTest(settings = TEST_ADMIN_SETTINGS) {
        client.register()
        val userToken = client.login().token().accessToken
        val adminToken = client.login(TEST_ADMIN_LOGIN).token().accessToken
        for (id in listOf("invalid", "999")) {
            client.delete("/tasks/$id").assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
            client.delete("/tasks/$id") { bearerAuth(userToken) }.assertError(HttpStatusCode.Forbidden, "FORBIDDEN")
        }
        client.delete("/tasks/invalid") { bearerAuth(adminToken) }.assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        client.delete("/tasks/999") { bearerAuth(adminToken) }.assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
    }

    @Test
    fun `role in signed JWT or request headers cannot promote a user`() = apiTest {
        val user = Json.decodeFromString<UserResponse>(client.register().bodyAsText())
        val forgedRoleToken = JWT.create()
            .withIssuer(TEST_JWT_ISSUER).withAudience(TEST_JWT_AUDIENCE)
            .withExpiresAt(Instant.now().plusSeconds(3600))
            .withClaim("userId", user.id.toString()).withClaim("role", "ADMIN")
            .sign(Algorithm.HMAC256(TEST_JWT_SECRET))
        val task = client.createTask(forgedRoleToken)
        client.delete("/tasks/${task.id}?role=ADMIN") {
            bearerAuth(forgedRoleToken)
            header("X-Role", "ADMIN")
        }.assertError(HttpStatusCode.Forbidden, "FORBIDDEN")
        assertEquals("USER", client.me(forgedRoleToken).role)
        assertEquals(HttpStatusCode.OK, client.get("/tasks/${task.id}").status)
    }

    @Test
    fun `role changes take effect with the same token and deleted users receive 401`() = apiTest(settings = TEST_ADMIN_SETTINGS) {
        val token = client.login(TEST_ADMIN_LOGIN).token().accessToken
        val admin = client.me(token)
        val task = client.createTask(token)
        setRole(admin.id, "USER")
        assertEquals("USER", client.me(token).role)
        client.delete("/tasks/${task.id}") { bearerAuth(token) }.assertError(HttpStatusCode.Forbidden, "FORBIDDEN")
        setRole(admin.id, "ADMIN")
        assertEquals("ADMIN", client.me(token).role)
        assertEquals(HttpStatusCode.NoContent, client.delete("/tasks/${task.id}") { bearerAuth(token) }.status)
        withConnection { connection ->
            connection.prepareStatement("DELETE FROM users WHERE id = ?").use { it.setLong(1, admin.id); it.executeUpdate() }
        }
        client.get("/auth/me") { bearerAuth(token) }.assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
    }

    private suspend fun HttpClient.me(token: String): UserResponse =
        Json.decodeFromString(get("/auth/me") { bearerAuth(token) }.bodyAsText())

    private suspend fun HttpClient.createTask(token: String): TaskResponse {
        val response = post("/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Role test"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private fun setRole(id: Long, role: String) = withConnection { connection ->
        connection.prepareStatement("UPDATE users SET role = ? WHERE id = ?").use {
            it.setString(1, role)
            it.setLong(2, id)
            assertEquals(1, it.executeUpdate())
        }
    }
}
