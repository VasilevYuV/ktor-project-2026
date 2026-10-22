package com.vasilevyuv.application

import com.vasilevyuv.auth.api.authRoutes
import com.vasilevyuv.auth.data.PostgresUserRepository
import com.vasilevyuv.auth.data.UsersTable
import com.vasilevyuv.auth.security.BCryptPasswordHasher
import com.vasilevyuv.auth.security.JwtSettings
import com.vasilevyuv.auth.security.JwtTokenService
import com.vasilevyuv.auth.service.AuthService
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
    val tokens = JwtTokenService(JwtSettings.from(environment.config))
    val connection = DatabaseConnection.open(DatabaseSettings.from(environment.config))
    try {
        transaction(connection.database) { SchemaUtils.create(TasksTable, UsersTable) }
        configureSerialization()
        configureErrors()

        val users = PostgresUserRepository(connection.database)
        val authService = AuthService(users, BCryptPasswordHasher())
        val taskService = TaskService(PostgresTaskRepository(connection.database))
        configureAuthentication(tokens, users)

        routing {
            get("/") {
                call.respond(mapOf("name" to "Task API", "checkpoint" to "KT2"))
            }
            authRoutes(authService, tokens)
            taskRoutes(taskService, JWT_AUTH)
        }
        monitor.subscribe(ApplicationStopped) { connection.close() }
    } catch (cause: Exception) {
        connection.close()
        throw cause
    }
}
