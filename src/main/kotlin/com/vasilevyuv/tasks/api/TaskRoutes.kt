package com.vasilevyuv.tasks.api

import com.vasilevyuv.auth.api.requireAdmin
import com.vasilevyuv.common.domain.ValidationException
import com.vasilevyuv.tasks.domain.TaskStatus
import com.vasilevyuv.tasks.service.TaskService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.taskRoutes(service: TaskService, authenticationProvider: String) {
    route("/tasks") {
        get {
            val status = call.request.queryParameters["status"]?.let(::parseTaskStatus)
            val query = call.request.queryParameters["q"]
            call.respond(service.list(status, query).map { it.toResponse() })
        }
        get("/{id}") {
            val id = parseTaskId(call.parameters["id"])
            call.respond(service.get(id).toResponse())
        }
        authenticate(authenticationProvider) {
            post {
                val request = call.receive<CreateTaskRequest>()
                val task = service.create(request.title, request.description, parseTaskStatus(request.status))
                call.response.headers.append(HttpHeaders.Location, "/tasks/${task.id}")
                call.respond(HttpStatusCode.Created, task.toResponse())
            }
            put("/{id}") {
                val id = parseTaskId(call.parameters["id"])
                val request = call.receive<UpdateTaskRequest>()
                val task = service.update(id, request.title, request.description, parseTaskStatus(request.status))
                call.respond(task.toResponse())
            }
            delete("/{id}") {
                call.requireAdmin()
                val id = parseTaskId(call.parameters["id"])
                service.delete(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun parseTaskId(value: String?): Long =
    value?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw ValidationException("id must be a positive integer")

private fun parseTaskStatus(value: String): TaskStatus =
    TaskStatus.entries.find { it.name == value }
        ?: throw ValidationException("status must be TODO, IN_PROGRESS or DONE")
