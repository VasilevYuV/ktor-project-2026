package com.vasilevyuv.application

import com.vasilevyuv.common.api.ApiError
import com.vasilevyuv.support.assertError
import com.vasilevyuv.tasks.api.CreateTaskRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ErrorsTest {
    @Test
    fun `unexpected exceptions return a safe JSON 500 and the server remains available`() = testApplication {
        application {
            configureSerialization()
            configureErrors()
            routing {
                get("/failure") { error("Internal database detail must not reach the client") }
                get("/healthy") { call.respond(mapOf("status" to "ok")) }
            }
        }
        val response = client.get("/failure")
        response.assertError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR")
        assertEquals(ApiError("INTERNAL_ERROR", "An unexpected error occurred"), Json.decodeFromString(response.bodyAsText()))
        assertFalse(response.bodyAsText().contains("database"))
        assertFalse(response.bodyAsText().contains("IllegalStateException"))
        assertEquals(HttpStatusCode.OK, client.get("/healthy").status)
    }

    @Test
    fun `malformed and unsupported bodies use the central error contract`() = testApplication {
        application {
            configureSerialization()
            configureErrors()
            routing { post("/body") { call.respond(call.receive<CreateTaskRequest>()) } }
        }
        val malformed = client.post("/body") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"secret body marker", "unexpected":true}""")
        }
        malformed.assertError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
        assertFalse(malformed.bodyAsText().contains("secret body marker"))
        client.post("/body") {
            contentType(ContentType.Text.Plain)
            setBody("secret body marker")
        }.assertError(HttpStatusCode.UnsupportedMediaType, "UNSUPPORTED_MEDIA_TYPE")
        client.get("/missing").assertError(HttpStatusCode.NotFound, "ROUTE_NOT_FOUND")
    }
}
