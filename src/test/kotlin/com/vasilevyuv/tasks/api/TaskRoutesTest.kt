package com.vasilevyuv.tasks.api

import com.vasilevyuv.support.PostgresApiTest
import com.vasilevyuv.support.assertError
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskRoutesTest : PostgresApiTest() {
    @Test
    fun `root describes the API in JSON`() = apiTest(authenticated = true) {
        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("Task API", Json.decodeFromString<Map<String, String>>(response.bodyAsText())["name"])
    }

    @Test
    fun `list starts empty`() = apiTest(authenticated = true) {
        val response = client.get("/tasks")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList(), response.tasks())
    }

    @Test
    fun `create assigns an id and defaults and returns a location`() = apiTest(authenticated = true) {
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
    fun `get returns the stored task with all fields`() = apiTest(authenticated = true) {
        val created = client.createTask("Read docs", "  Ktor routing  ", "IN_PROGRESS").task()
        val response = client.get("/tasks/${created.id}")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(created, response.task())
        assertEquals("Ktor routing", created.description)
        assertEquals("IN_PROGRESS", created.status)
    }

    @Test
    fun `list is ordered by id and filters by status`() = apiTest(authenticated = true) {
        val first = client.createTask("First").task()
        val done = client.createTask("Second", status = "DONE").task()
        val third = client.createTask("Third").task()

        assertEquals(listOf(first, done, third), client.get("/tasks").tasks())
        assertEquals(listOf(done), client.get("/tasks?status=DONE").tasks())
        assertEquals(emptyList(), client.get("/tasks?status=IN_PROGRESS").tasks())
    }

    @Test
    fun `search ignores case and combines with the status filter`() = apiTest(authenticated = true) {
        client.createTask("Learn Kotlin")
        val matching = client.createTask("Read KOTLIN docs", status = "DONE").task()
        client.createTask("Learn Ktor", status = "DONE")

        val response = client.get("/tasks?status=DONE&q=%20kotlin%20")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(matching), response.tasks())
        assertEquals(emptyList(), client.get("/tasks?q=missing").tasks())
    }

    @Test
    fun `blank search means all tasks`() = apiTest(authenticated = true) {
        val task = client.createTask("Learn Kotlin").task()

        assertEquals(listOf(task), client.get("/tasks?q=%20%20").tasks())
    }

    @Test
    fun `delete returns an empty 204 and removes only the requested task`() = apiTest(authenticated = true) {
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
    fun `ids are not reused after deletion`() = apiTest(authenticated = true) {
        val removed = client.createTask("First").task()
        client.delete("/tasks/${removed.id}")
        val created = client.createTask("Second").task()

        assertTrue(created.id > removed.id)
    }

    @Test
    fun `invalid path ids return 400`() = apiTest(authenticated = true) {
        for (id in listOf("abc", "0", "-1", "1.5", "9223372036854775808")) {
            client.get("/tasks/$id").assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            client.delete("/tasks/$id").assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            client.updateTask(id).assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }
    }

    @Test
    fun `missing tasks return 404`() = apiTest(authenticated = true) {
        client.get("/tasks/999").assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
        client.delete("/tasks/999").assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
        client.updateTask("999").assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
        assertEquals(emptyList(), client.get("/tasks").tasks())
    }

    @Test
    fun `unknown routes return a JSON error`() = apiTest(authenticated = true) {
        client.get("/missing").assertError(HttpStatusCode.NotFound, "ROUTE_NOT_FOUND")
    }

    @Test
    fun `invalid statuses are rejected in query and body`() = apiTest(authenticated = true) {
        for (status in listOf("", "done", "UNKNOWN")) {
            client.get("/tasks?status=$status").assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            client.createTask("Task", status = status)
                .assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }
        assertEquals(emptyList(), client.get("/tasks").tasks())
    }

    @Test
    fun `blank or oversized fields are rejected without creating tasks`() = apiTest(authenticated = true) {
        for (title in listOf("", "   ", "a".repeat(201))) {
            client.createTask(title).assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }
        client.createTask("Task", description = "a".repeat(2_001))
            .assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        assertEquals(emptyList(), client.get("/tasks").tasks())
    }

    @Test
    fun `field length limits are inclusive`() = apiTest(authenticated = true) {
        val response = client.createTask("a".repeat(200), "b".repeat(2_000))

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(200, response.task().title.length)
        assertEquals(2_000, response.task().description.length)
    }

    @Test
    fun `oversized search returns 400`() = apiTest(authenticated = true) {
        client.get("/tasks?q=${"a".repeat(201)}").assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
    }

    @Test
    fun `malformed JSON and invalid body shapes return 400`() = apiTest(authenticated = true) {
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
    fun `non JSON request body returns 415`() = apiTest(authenticated = true) {
        client.post("/tasks") {
            contentType(ContentType.Text.Plain)
            setBody("Task")
        }.assertError(HttpStatusCode.UnsupportedMediaType, "UNSUPPORTED_MEDIA_TYPE")
    }

    @Test
    fun `put replaces all fields and preserves the id`() = apiTest(authenticated = true) {
        val created = client.createTask("Before", "Old description").task()
        val other = client.createTask("Keep me").task()

        val response = client.updateTask(created.id.toString(), "  After  ", "  New description  ", "DONE")
        val expected = TaskResponse(created.id, "After", "New description", "DONE")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, response.task())
        assertEquals(expected, client.get("/tasks/${created.id}").task())
        assertEquals(other, client.get("/tasks/${other.id}").task())
        assertEquals(listOf(expected), client.get("/tasks?status=DONE&q=after").tasks())
    }

    @Test
    fun `repeating put is idempotent and allows clearing the description`() = apiTest(authenticated = true) {
        val created = client.createTask("Before", "Description").task()

        repeat(2) {
            val response = client.updateTask(created.id.toString(), "After", "", "TODO")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(TaskResponse(created.id, "After", "", "TODO"), response.task())
        }
        assertEquals(1, client.get("/tasks").tasks().size)
    }

    @Test
    fun `invalid update leaves the existing task unchanged`() = apiTest(authenticated = true) {
        val created = client.createTask("Original", "Keep this", "IN_PROGRESS").task()
        val id = created.id.toString()

        for (title in listOf("", "   ", "a".repeat(201))) {
            client.updateTask(id, title = title).assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }
        client.updateTask(id, description = "a".repeat(2_001))
            .assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        client.updateTask(id, status = "unknown").assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")

        assertEquals(created, client.get("/tasks/$id").task())
    }

    @Test
    fun `put requires all fields and rejects malformed bodies and client ids`() = apiTest(authenticated = true) {
        val created = client.createTask("Original").task()
        val bodies = listOf(
            "{", "{}", "[]", "null",
            """{"title":"After"}""",
            """{"title":"After","description":""}""",
            """{"title":"After","status":"DONE"}""",
            """{"title":"After","description":null,"status":"DONE"}""",
            """{"title":42,"description":"","status":"DONE"}""",
            """{"title":"After","description":"","status":"DONE","id":99}""",
        )
        for (body in bodies) {
            client.put("/tasks/${created.id}") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.assertError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
        }
        assertEquals(created, client.get("/tasks/${created.id}").task())
    }

    @Test
    fun `put rejects non JSON bodies`() = apiTest(authenticated = true) {
        val created = client.createTask("Original").task()
        client.put("/tasks/${created.id}") {
            contentType(ContentType.Text.Plain)
            setBody("After")
        }.assertError(HttpStatusCode.UnsupportedMediaType, "UNSUPPORTED_MEDIA_TYPE")
        assertEquals(created, client.get("/tasks/${created.id}").task())
    }

    @Test
    fun `update accepts the maximum field lengths`() = apiTest(authenticated = true) {
        val created = client.createTask("Original").task()
        val response = client.updateTask(created.id.toString(), "a".repeat(200), "b".repeat(2_000))

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(200, client.get("/tasks/${created.id}").task().title.length)
        assertEquals(2_000, client.get("/tasks/${created.id}").task().description.length)
    }

    @Test
    fun `search treats SQL wildcards and quotes as literal text`() = apiTest(authenticated = true) {
        client.createTask("Ordinary task")
        for (query in listOf("%", "_", "\\", "' OR 1=1 --")) {
            val matching = client.createTask("Search $query here").task()
            val response = client.get("/tasks") { parameter("q", query) }

            assertEquals(listOf(matching), response.tasks())
        }
    }

    @Test
    fun `database preserves Unicode and searches Cyrillic without case sensitivity`() = apiTest(authenticated = true) {
        val created = client.createTask("Изучить Kotlin", "Задача с кириллицей").task()
        val response = client.get("/tasks") { parameter("q", "ИЗУЧИТЬ") }

        assertEquals(listOf(created), response.tasks())
        assertEquals(created, client.get("/tasks/${created.id}").task())
    }

    @Test
    fun `concurrent creates produce distinct persisted tasks`() = apiTest(authenticated = true) {
        val created = coroutineScope {
            (1..20).map { index ->
                async {
                    val response = client.createTask("Task $index")
                    assertEquals(HttpStatusCode.Created, response.status)
                    response.task()
                }
            }.awaitAll()
        }

        assertEquals(20, created.map { it.id }.toSet().size)
        assertEquals(created.sortedBy { it.id }, client.get("/tasks").tasks())
    }

    @Test
    fun `changes survive application and connection pool restarts`() {
        lateinit var saved: TaskResponse
        apiTest(authenticated = true) {
            saved = client.createTask("Persist me", "Initial").task()
            saved = client.updateTask(saved.id.toString(), "Updated", "Persisted", "DONE").task()
        }

        apiTest(resetData = false, authenticated = true) {
            assertEquals(saved, client.get("/tasks/${saved.id}").task())
            val next = client.createTask("Next task").task()
            assertTrue(next.id > saved.id)
            assertEquals(HttpStatusCode.NoContent, client.delete("/tasks/${saved.id}").status)
        }

        apiTest(resetData = false, authenticated = true) {
            client.get("/tasks/${saved.id}").assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
            assertEquals("Next task", client.get("/tasks").tasks().single().title)
        }
    }

    private suspend fun HttpClient.updateTask(
        id: String,
        title: String = "Updated",
        description: String = "",
        status: String = "DONE",
    ): HttpResponse = put("/tasks/$id") {
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(UpdateTaskRequest(title, description, status)))
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

}
