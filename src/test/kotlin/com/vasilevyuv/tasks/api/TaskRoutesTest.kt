package com.vasilevyuv.tasks.api

import com.vasilevyuv.common.api.ApiError
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskRoutesTest {
    @Test
    fun `root describes the API in JSON`() = apiTest {
        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("Task API", Json.decodeFromString<Map<String, String>>(response.bodyAsText())["name"])
    }

    @Test
    fun `list starts empty`() = apiTest {
        val response = client.get("/tasks")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList(), response.tasks())
    }

    @Test
    fun `create assigns an id and defaults and returns a location`() = apiTest {
        val response = client.createTask("  Learn Kotlin  ")
        val task = response.task()

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertTrue(task.id > 0)
        assertEquals("/tasks/${task.id}", response.headers[HttpHeaders.Location])
        assertEquals("Learn Kotlin", task.title)
        assertEquals("", task.description)
        assertEquals("TODO", task.status)
    }

    @Test
    fun `get returns the stored task with all fields`() = apiTest {
        val created = client.createTask("Read docs", "  Ktor routing  ", "IN_PROGRESS").task()
        val response = client.get("/tasks/${created.id}")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(created, response.task())
        assertEquals("Ktor routing", created.description)
        assertEquals("IN_PROGRESS", created.status)
    }

    @Test
    fun `list is ordered by id and filters by status`() = apiTest {
        val first = client.createTask("First").task()
        val done = client.createTask("Second", status = "DONE").task()
        val third = client.createTask("Third").task()

        assertEquals(listOf(first, done, third), client.get("/tasks").tasks())
        assertEquals(listOf(done), client.get("/tasks?status=DONE").tasks())
        assertEquals(emptyList(), client.get("/tasks?status=IN_PROGRESS").tasks())
    }

    @Test
    fun `search ignores case and combines with the status filter`() = apiTest {
        client.createTask("Learn Kotlin")
        val matching = client.createTask("Read KOTLIN docs", status = "DONE").task()
        client.createTask("Learn Ktor", status = "DONE")

        val response = client.get("/tasks?status=DONE&q=%20kotlin%20")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(matching), response.tasks())
        assertEquals(emptyList(), client.get("/tasks?q=missing").tasks())
    }

    @Test
    fun `blank search means all tasks`() = apiTest {
        val task = client.createTask("Learn Kotlin").task()

        assertEquals(listOf(task), client.get("/tasks?q=%20%20").tasks())
    }

    @Test
    fun `delete returns an empty 204 and removes only the requested task`() = apiTest {
        val removed = client.createTask("Remove me").task()
        val remaining = client.createTask("Keep me").task()

        val response = client.delete("/tasks/${removed.id}")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("", response.bodyAsText())
        client.get("/tasks/${removed.id}").assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
        client.delete("/tasks/${removed.id}").assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
        assertEquals(listOf(remaining), client.get("/tasks").tasks())
    }

    @Test
    fun `ids are not reused after deletion`() = apiTest {
        val removed = client.createTask("First").task()
        client.delete("/tasks/${removed.id}")
        val created = client.createTask("Second").task()

        assertTrue(created.id > removed.id)
    }

    @Test
    fun `invalid path ids return 400`() = apiTest {
        for (id in listOf("abc", "0", "-1", "1.5", "9223372036854775808")) {
            client.get("/tasks/$id").assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            client.delete("/tasks/$id").assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }
    }

    @Test
    fun `missing tasks return 404`() = apiTest {
        client.get("/tasks/999").assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
        client.delete("/tasks/999").assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
    }

    @Test
    fun `unknown routes return a JSON error`() = apiTest {
        client.get("/missing").assertError(HttpStatusCode.NotFound, "ROUTE_NOT_FOUND")
    }

    @Test
    fun `invalid statuses are rejected in query and body`() = apiTest {
        for (status in listOf("", "done", "UNKNOWN")) {
            client.get("/tasks?status=$status").assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            client.createTask("Task", status = status)
                .assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }
        assertEquals(emptyList(), client.get("/tasks").tasks())
    }

    @Test
    fun `blank or oversized fields are rejected without creating tasks`() = apiTest {
        for (title in listOf("", "   ", "a".repeat(201))) {
            client.createTask(title).assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }
        client.createTask("Task", description = "a".repeat(2_001))
            .assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        assertEquals(emptyList(), client.get("/tasks").tasks())
    }

    @Test
    fun `field length limits are inclusive`() = apiTest {
        val response = client.createTask("a".repeat(200), "b".repeat(2_000))

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(200, response.task().title.length)
        assertEquals(2_000, response.task().description.length)
    }

    @Test
    fun `oversized search returns 400`() = apiTest {
        client.get("/tasks?q=${"a".repeat(201)}").assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
    }

    @Test
    fun `malformed JSON and invalid body shapes return 400`() = apiTest {
        val bodies = listOf(
            "",
            "{",
            "{}",
            "[]",
            "null",
            """{"title": null}""",
            """{"title": 42}""",
            """{"title": "Task", "id": 100}""",
        )
        for (body in bodies) {
            client.post("/tasks") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.assertError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
        }
        assertEquals(emptyList(), client.get("/tasks").tasks())
    }

    @Test
    fun `non JSON request body returns 415`() = apiTest {
        client.post("/tasks") {
            contentType(ContentType.Text.Plain)
            setBody("Task")
        }.assertError(HttpStatusCode.UnsupportedMediaType, "UNSUPPORTED_MEDIA_TYPE")
    }

    private fun apiTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        // Load application.yaml as the real server does; each test gets a fresh repository.
        configure()
        block()
    }

    private suspend fun HttpClient.createTask(
        title: String,
        description: String = "",
        status: String = "TODO",
    ): HttpResponse = post("/tasks") {
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(CreateTaskRequest(title, description, status)))
    }

    private suspend fun HttpResponse.task(): TaskResponse = Json.decodeFromString(bodyAsText())

    private suspend fun HttpResponse.tasks(): List<TaskResponse> = Json.decodeFromString(bodyAsText())

    private suspend fun HttpResponse.assertError(expectedStatus: HttpStatusCode, expectedCode: String) {
        assertEquals(expectedStatus, status, bodyAsText())
        assertEquals(ContentType.Application.Json, contentType()?.withoutParameters())
        val error = Json.decodeFromString<ApiError>(bodyAsText())
        assertEquals(expectedCode, error.code)
        assertTrue(error.message.isNotBlank())
    }
}
