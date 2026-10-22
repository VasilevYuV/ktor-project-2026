package com.vasilevyuv.application

import com.vasilevyuv.infrastructure.database.DatabaseConnection
import com.vasilevyuv.infrastructure.database.DatabaseSettings
import com.vasilevyuv.tasks.api.taskRoutes
import com.vasilevyuv.tasks.data.PostgresTaskRepository
import com.vasilevyuv.tasks.data.TasksTable
import com.vasilevyuv.tasks.service.TaskService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.module() {
    val connection = DatabaseConnection.open(DatabaseSettings.from(environment.config))
    try {
        transaction(connection.database) { SchemaUtils.create(TasksTable) }
        configureSerialization()
        configureErrors()

        val taskService = TaskService(PostgresTaskRepository(connection.database))

        routing {
            get("/") {
                call.respond(mapOf("name" to "Task API", "checkpoint" to "KT2 CRUD"))
            }
            taskRoutes(taskService)
        }
        monitor.subscribe(ApplicationStopped) { connection.close() }
    } catch (cause: Exception) {
        connection.close()
        throw cause
    }
}
