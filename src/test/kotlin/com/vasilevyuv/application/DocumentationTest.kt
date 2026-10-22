package com.vasilevyuv.application

import com.vasilevyuv.auth.api.UserResponse
import com.vasilevyuv.support.PostgresApiTest
import com.vasilevyuv.support.assertError
import com.vasilevyuv.support.token
import com.vasilevyuv.tasks.api.TaskResponse
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
import io.ktor.http.contentType
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem.HttpMethod
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.swagger.v3.core.util.Json as SwaggerJson

class DocumentationTest : PostgresApiTest() {
    @Test
    fun `OpenAPI is public valid and covers every application endpoint`() = apiTest {
        val response = client.get("/openapi.yaml")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Yaml, response.contentType()?.withoutParameters())
        val api = parseSpecification(response.bodyAsText())
        assertEquals("3.0.3", api.openapi)
        assertEquals("Task API", api.info.title)
        assertEquals(listOf("/"), api.servers.map { it.url })
        val expected = mapOf(
            "/" to setOf(HttpMethod.GET),
            "/auth/register" to setOf(HttpMethod.POST),
            "/auth/login" to setOf(HttpMethod.POST),
            "/tasks" to setOf(HttpMethod.GET, HttpMethod.POST),
            "/tasks/{id}" to setOf(HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE),
            "/docs" to setOf(HttpMethod.GET),
            "/openapi.yaml" to setOf(HttpMethod.GET),
        )
        assertEquals(expected, api.paths.mapValues { (_, path) -> path.readOperationsMap().keys })
        val operations = api.paths.values.flatMap { it.readOperations() }
        assertEquals(operations.size, operations.map { it.operationId }.toSet().size)
        assertTrue(operations.all { !it.summary.isNullOrBlank() && !it.responses.isNullOrEmpty() })
    }

    @Test
    fun `specification documents JWT only for task writes and matches request DTOs`() = apiTest {
        val api = parseSpecification(client.get("/openapi.yaml").bodyAsText())
        val bearer = api.components.securitySchemes.getValue("bearerAuth")
        assertEquals(SecurityScheme.Type.HTTP, bearer.type)
        assertEquals("bearer", bearer.scheme)
        assertEquals("JWT", bearer.bearerFormat)
        for ((path, item) in api.paths) {
            for ((method, operation) in item.readOperationsMap()) {
                val requiresToken = path.startsWith("/tasks") && method != HttpMethod.GET
                val security = operation.security ?: api.security
                assertEquals(requiresToken, !security.isNullOrEmpty(), "$method $path")
                if (requiresToken) {
                    assertEquals(setOf("bearerAuth"), security.single().keys)
                    assertTrue(operation.responses.containsKey("401"))
                }
            }
        }
        val schemas = api.components.schemas
        assertEquals(listOf("title"), schemas.getValue("CreateTaskRequest").required)
        assertEquals(setOf("title", "description", "status"), schemas.getValue("UpdateTaskRequest").required.toSet())
        for (name in listOf("CreateTaskRequest", "UpdateTaskRequest", "Credentials")) {
            assertEquals(false, schemas.getValue(name).additionalProperties)
        }
        assertEquals(setOf("id", "login"), schemas.getValue("UserResponse").properties.keys)
        assertEquals(true, schemas.getValue("Credentials").properties.getValue("password").writeOnly)
        assertEquals(listOf("TODO", "IN_PROGRESS", "DONE"), schemas.getValue("TaskStatus").enum)
        assertTrue(api.paths.getValue("/tasks/{id}").delete.responses.getValue("204").content.isNullOrEmpty())
    }

    @Test
    fun `Swagger UI and all its assets are public and served locally`() = apiTest {
        for (path in listOf("/docs", "/docs/")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
            val html = response.bodyAsText()
            assertTrue(html.contains("id=\"swagger-ui\""))
            assertFalse(html.contains("\${swaggerUiVersion}"))
            val resources = Regex("(?:src|href)=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.toList()
            assertEquals(4, resources.size)
            for (resource in resources) {
                assertTrue(resource.startsWith("/docs/"), resource)
                val asset = client.get(resource)
                assertEquals(HttpStatusCode.OK, asset.status, resource)
                assertTrue(asset.bodyAsText().isNotEmpty(), resource)
            }
        }
        val script = client.get("/docs/swagger-initializer.js").bodyAsText()
        assertTrue(script.contains("url: \"/openapi.yaml\""))
        assertTrue(script.contains("persistAuthorization: false"))
        assertTrue(script.contains("validatorUrl: null"))
    }

    @Test
    fun `documentation routes do not expose application configuration or arbitrary resources`() = apiTest {
        for (path in listOf("/docs/missing.js", "/docs/application.yaml", "/docs/assets/application.yaml",
            "/docs/assets/logback.xml")) {
            client.get(path).assertError(HttpStatusCode.NotFound, "ROUTE_NOT_FOUND")
        }
        client.get("/docs/%2e%2e/application.yaml").assertError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
    }

    @Test
    fun `OpenAPI examples execute registration login and the complete CRUD cycle`() = apiTest {
        val api = parseSpecification(client.get("/openapi.yaml").bodyAsText())
        fun example(name: String): String = SwaggerJson.mapper().writeValueAsString(api.components.schemas.getValue(name).example)

        val credentials = example("Credentials")
        val registration = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }
        assertEquals(HttpStatusCode.Created, registration.status)
        assertEquals("student", Json.decodeFromString<UserResponse>(registration.bodyAsText()).login)
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }.assertError(HttpStatusCode.Conflict, "LOGIN_ALREADY_TAKEN")
        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val token = login.token().accessToken
        val created = client.post("/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(example("CreateTaskRequest"))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val task = Json.decodeFromString<TaskResponse>(created.bodyAsText())
        val location = assertNotNull(created.headers[HttpHeaders.Location])
        assertEquals(task, Json.decodeFromString<TaskResponse>(client.get(location).bodyAsText()))
        assertEquals(listOf(task), Json.decodeFromString<List<TaskResponse>>(client.get("/tasks?status=IN_PROGRESS&q=Ktor").bodyAsText()))
        val update = client.put(location) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(example("UpdateTaskRequest"))
        }
        assertEquals(HttpStatusCode.OK, update.status)
        assertEquals("DONE", Json.decodeFromString<TaskResponse>(update.bodyAsText()).status)
        client.put(location) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Incomplete"}""")
        }.assertError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
        client.delete(location).assertError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        val deleted = client.delete(location) { bearerAuth(token) }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals("", deleted.bodyAsText())
        client.get(location).assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
    }

    private fun parseSpecification(yaml: String): OpenAPI {
        val result = OpenAPIV3Parser().readContents(yaml, null, ParseOptions().apply { isResolve = true })
        assertTrue(result.messages.isNullOrEmpty(), result.messages.orEmpty().joinToString("\n"))
        return assertNotNull(result.openAPI)
    }
}
