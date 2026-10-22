package com.vasilevyuv.application

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.vasilevyuv.auth.domain.InvalidCredentialsException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestLoggingTest {
    @Test
    fun `requests log method path final status and duration exactly once`() = captureLogs { events ->
        testApplication {
            application {
                configureSerialization()
                configureErrors()
                configureRequestLogging()
                routing {
                    get("/success") { call.respond(mapOf("ok" to true)) }
                    post("/empty") { call.respond(HttpStatusCode.NoContent) }
                    get("/failure") { error("Intentional test exception") }
                    get("/unauthorized") { throw InvalidCredentialsException() }
                }
            }
            client.get("/success").bodyAsText()
            client.post("/empty").bodyAsText()
            client.get("/failure").bodyAsText()
            client.get("/unauthorized").bodyAsText()
            client.get("/missing").bodyAsText()
        }
        val expected = setOf(
            "method=GET path=/success status=200",
            "method=POST path=/empty status=204",
            "method=GET path=/failure status=500",
            "method=GET path=/unauthorized status=401",
            "method=GET path=/missing status=404",
        )
        assertEquals(expected, events.map { it.formattedMessage.substringBefore(" durationMs=") }.toSet())
        assertEquals(expected.size, events.size)
        assertTrue(events.all { it.level == Level.INFO })
        assertTrue(events.all { it.formattedMessage.substringAfter(" durationMs=").toLong() >= 0 })
    }

    @Test
    fun `request logging excludes query headers and body secrets`() = captureLogs { events ->
        testApplication {
            application {
                configureSerialization()
                configureErrors()
                configureRequestLogging()
                routing {
                    post("/auth/login") {
                        call.receiveText()
                        call.respond(mapOf("accessToken" to "response-token-marker"))
                    }
                }
            }
            client.post("/auth/login?password=query-secret-marker") {
                bearerAuth("header-token-marker")
                header(HttpHeaders.Cookie, "session=cookie-secret-marker")
                contentType(ContentType.Application.Json)
                setBody("""{"login":"student","password":"body-secret-marker"}""")
            }.bodyAsText()
        }
        assertEquals(1, events.size)
        val message = events.single().formattedMessage
        assertTrue(message.startsWith("method=POST path=/auth/login status=200 durationMs="))
        for (secret in listOf("query-secret-marker", "header-token-marker", "cookie-secret-marker",
            "body-secret-marker", "response-token-marker", "student")) {
            assertFalse(message.contains(secret))
        }
    }

    private fun captureLogs(block: (List<ILoggingEvent>) -> Unit) {
        val logger = LoggerFactory.getLogger("http.requests") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            block(appender.list)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}
