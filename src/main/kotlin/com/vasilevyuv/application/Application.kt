package com.vasilevyuv.application

import com.vasilevyuv.tasks.api.taskRoutes
import com.vasilevyuv.tasks.data.InMemoryTaskRepository
import com.vasilevyuv.tasks.service.TaskService
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.module() {
    configureSerialization()
    configureErrors()

    val taskService = TaskService(InMemoryTaskRepository())

    routing {
        get("/") {
            call.respond(mapOf("name" to "Task API", "checkpoint" to "KT1"))
        }
        taskRoutes(taskService)
    }
}
