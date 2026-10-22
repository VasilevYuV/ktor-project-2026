package com.vasilevyuv.notifications.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.vasilevyuv.auth.api.UserResponse
import com.vasilevyuv.support.ApiTestContext
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
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NotificationRoutesTest : PostgresApiTest() {
    @Test
    fun `handshake requires a valid Bearer token and ignores query tokens`() = apiTest {
        val user = Json.decodeFromString<UserResponse>(client.register().bodyAsText())
        val validToken = client.login().token().accessToken
        val invalidTokens = listOf(
            null, "garbage", signedToken(user.id, Instant.now().minusSeconds(60)),
            signedToken(user.id, secret = "another-secret-at-least-32-bytes-long"),
            signedToken(user.id, issuer = "another-issuer"),
            signedToken(user.id, audience = "another-audience"),
            signedToken(999),
        )
        for (token in invalidTokens) {
            // Inspect the rejected HTTP handshake without asking Test Engine to create a WebSocket session.
            val response = client.get("/ws/tasks?token=$validToken") {
                setBody(object : OutgoingContent.NoContent() {
                    override val headers = headersOf(
                        HttpHeaders.Connection to listOf("Upgrade"),
                        HttpHeaders.Upgrade to listOf("websocket"),
                        HttpHeaders.SecWebSocketVersion to listOf("13"),
                        HttpHeaders.SecWebSocketKey to listOf("dGhlIHNhbXBsZSBub25jZQ=="),
                    )
                })
                if (token != null) bearerAuth(token)
            }
            response.assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
            assertTrue(response.headers[HttpHeaders.WWWAuthenticate]?.startsWith("Bearer ") == true)
        }
    }

    @Test
    fun `USER and ADMIN both receive each committed CRUD change`() = apiTest(settings = TEST_ADMIN_SETTINGS) {
        client.register()
        val userToken = client.login().token().accessToken
        val adminToken = client.login(TEST_ADMIN_LOGIN).token().accessToken
        withNotifications(userToken, adminToken) { sessions ->
            val task = client.createTask(userToken)
            sessions.forEach { it.expectChange("TASK_CREATED", task.id) }
            assertEquals(task, Json.decodeFromString<TaskResponse>(client.get("/tasks/${task.id}").bodyAsText()))
            val updated = client.put("/tasks/${task.id}") {
                bearerAuth(userToken)
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Updated","description":"","status":"DONE"}""")
            }
            assertEquals(HttpStatusCode.OK, updated.status)
            sessions.forEach { it.expectChange("TASK_UPDATED", task.id) }
            assertEquals("DONE", Json.decodeFromString<TaskResponse>(client.get("/tasks/${task.id}").bodyAsText()).status)
            assertEquals(HttpStatusCode.NoContent, client.delete("/tasks/${task.id}") { bearerAuth(adminToken) }.status)
            sessions.forEach { it.expectChange("TASK_DELETED", task.id) }
            client.get("/tasks/${task.id}").assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
        }
    }

    @Test
    fun `failed writes and reads do not produce notifications`() = apiTest(settings = TEST_ADMIN_SETTINGS) {
        client.register()
        val token = client.login().token().accessToken
        val adminToken = client.login(TEST_ADMIN_LOGIN).token().accessToken
        withNotifications(token) { (session) ->
            val task = client.createTask(token)
            session.expectChange("TASK_CREATED", task.id)
            client.get("/tasks")
            client.get("/tasks/${task.id}")
            client.post("/tasks").assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
            client.post("/tasks") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody("""{"title":" "}""")
            }.assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            client.put("/tasks/${task.id}") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody("{")
            }.assertError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
            client.put("/tasks/999") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody("""{"title":"Missing","description":"","status":"TODO"}""")
            }.assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
            client.delete("/tasks/${task.id}") { bearerAuth(token) }.assertError(HttpStatusCode.Forbidden, "FORBIDDEN")
            client.delete("/tasks/999") { bearerAuth(adminToken) }.assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
            // A successful write is a barrier: the next event must be this one, with no failed operations queued ahead of it.
            val next = client.createTask(token)
            session.expectChange("TASK_CREATED", next.id)
        }
    }

    @Test
    fun `disconnect and reconnect do not replay history or interrupt another client`() = apiTest {
        client.register()
        val token = client.login().token().accessToken
        withNotifications(token, token) { (first, second) ->
            val task = client.createTask(token)
            first.expectChange("TASK_CREATED", task.id)
            second.expectChange("TASK_CREATED", task.id)
            first.close()
            withTimeout(5_000) { first.closeReason.await() }
            val whileDisconnected = client.createTask(token)
            second.expectChange("TASK_CREATED", whileDisconnected.id)
            withNotifications(token) { (reconnected) ->
                val next = client.createTask(token)
                second.expectChange("TASK_CREATED", next.id)
                reconnected.expectChange("TASK_CREATED", next.id)
            }
        }
    }

    @Test
    fun `concurrent creates reach every subscriber exactly once`() = apiTest {
        client.register()
        val token = client.login().token().accessToken
        withNotifications(token, token) { sessions ->
            val tasks = coroutineScope { (1..12).map { async { client.createTask(token) } }.awaitAll() }
            val first = List(tasks.size) { sessions[0].nextNotification() }
            val second = List(tasks.size) { sessions[1].nextNotification() }
            assertEquals(first, second)
            assertTrue(first.all { it.type == "TASK_CREATED" })
            assertEquals(tasks.map { it.id }.toSet(), first.map { it.taskId }.toSet())
        }
    }

    @Test
    fun `client messages cannot change tasks or broadcast forged events`() = apiTest {
        client.register()
        val token = client.login().token().accessToken
        withNotifications(token, token) { (sender, observer) ->
            sender.send("""{"type":"TASK_DELETED","taskId":1}""")
            assertEquals(1008.toShort(), withTimeout(5_000) { sender.closeReason.await() }?.code)
            assertEquals("[]", client.get("/tasks").bodyAsText())
            val task = client.createTask(token)
            observer.expectChange("TASK_CREATED", task.id)
        }
    }

    @Test
    fun `connection closes when its JWT expires even without task changes`() = apiTest {
        val user = Json.decodeFromString<UserResponse>(client.register().bodyAsText())
        val token = signedToken(user.id, Instant.now().plusSeconds(3))
        withNotifications(token) { (session) ->
            val reason = assertNotNull(withTimeout(6_000) { session.closeReason.await() })
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason.code)
        }
    }

    @Test
    fun `deleted user stops receiving notifications on an existing connection`() = apiTest(settings = TEST_ADMIN_SETTINGS) {
        val user = Json.decodeFromString<UserResponse>(client.register().bodyAsText())
        val token = client.login().token().accessToken
        val adminToken = client.login(TEST_ADMIN_LOGIN).token().accessToken
        withNotifications(token) { (session) ->
            withConnection { connection ->
                connection.prepareStatement("DELETE FROM users WHERE id = ?").use { it.setLong(1, user.id); it.executeUpdate() }
            }
            client.createTask(adminToken)
            assertEquals(1008.toShort(), withTimeout(5_000) { session.closeReason.await() }?.code)
            assertTrue(withTimeout(5_000) { session.incoming.receiveCatching() }.isClosed)
        }
    }

    private suspend fun ApiTestContext.withNotifications(
        vararg tokens: String,
        block: suspend (List<DefaultClientWebSocketSession>) -> Unit,
    ) {
        val sessions = mutableListOf<DefaultClientWebSocketSession>()
        try {
            for (token in tokens) {
                val session = webSocketClient.webSocketSession("/ws/tasks") { bearerAuth(token) }
                sessions.add(session)
                assertEquals(TaskNotification("READY"), session.nextNotification())
            }
            block(sessions)
        } finally {
            sessions.forEach { it.close(); it.cancel() }
        }
    }

    private suspend fun DefaultClientWebSocketSession.nextNotification(): TaskNotification {
        val frame = assertIs<Frame.Text>(withTimeout(5_000) { incoming.receive() })
        val json = Json.parseToJsonElement(frame.readText()).jsonObject
        assertEquals(if (json.size == 1) setOf("type") else setOf("type", "taskId"), json.keys)
        return Json.decodeFromString(frame.readText())
    }

    private suspend fun DefaultClientWebSocketSession.expectChange(type: String, id: Long) {
        assertEquals(TaskNotification(type, id), nextNotification())
    }

    private suspend fun HttpClient.createTask(token: String): TaskResponse {
        val response = post("/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Notification test"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private fun signedToken(
        userId: Long,
        expiresAt: Instant = Instant.now().plusSeconds(3600),
        secret: String = TEST_JWT_SECRET,
        issuer: String = TEST_JWT_ISSUER,
        audience: String = TEST_JWT_AUDIENCE,
    ): String = JWT.create().withIssuer(issuer).withAudience(audience)
        .withClaim("userId", userId.toString()).withExpiresAt(expiresAt).sign(Algorithm.HMAC256(secret))
}
