package com.vasilevyuv.support

import com.vasilevyuv.application.module
import com.vasilevyuv.auth.api.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.AfterClass
import org.junit.BeforeClass
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import kotlin.test.assertEquals

abstract class PostgresApiTest {
    protected fun apiTest(
        resetData: Boolean = true,
        authenticated: Boolean = false,
        block: suspend ApiTestContext.() -> Unit,
    ) = testApplication {
        environment {
            config = MapApplicationConfig(
                "database.url" to postgres.jdbcUrl,
                "database.user" to postgres.username,
                "database.password" to postgres.password,
                "database.poolSize" to "3",
                "jwt.secret" to TEST_JWT_SECRET,
                "jwt.issuer" to TEST_JWT_ISSUER,
                "jwt.audience" to TEST_JWT_AUDIENCE,
                "jwt.expiresInSeconds" to "3600",
            )
        }
        application { module() }
        startApplication()
        if (resetData) {
            // Only the disposable database owned by this test class is reset.
            withConnection { connection ->
                connection.createStatement().use { it.execute("TRUNCATE TABLE tasks, users RESTART IDENTITY") }
            }
        }
        val requestClient = if (authenticated) {
            if (resetData) assertEquals(HttpStatusCode.Created, client.register("task_tester").status)
            val response = client.login("task_tester")
            assertEquals(HttpStatusCode.OK, response.status)
            val token = Json.decodeFromString<TokenResponse>(response.bodyAsText()).accessToken
            createClient { defaultRequest { bearerAuth(token) } }
        } else {
            client
        }
        ApiTestContext(requestClient).block()
    }

    protected fun <T> withConnection(block: (Connection) -> T): T = postgres.createConnection("").use(block)

    companion object {
        private val postgres = PostgreSQLContainer("postgres:18.6-alpine")

        @BeforeClass
        @JvmStatic
        fun startDatabase() { postgres.start() }

        @AfterClass
        @JvmStatic
        fun stopDatabase() { postgres.stop() }
    }
}

class ApiTestContext(val client: HttpClient)
