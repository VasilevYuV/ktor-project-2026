package com.vasilevyuv.application

import com.vasilevyuv.auth.api.authRoutes
import com.vasilevyuv.auth.data.PostgresUserRepository
import com.vasilevyuv.auth.security.AdminBootstrapSettings
import com.vasilevyuv.auth.security.BCryptPasswordHasher
import com.vasilevyuv.auth.security.JwtSettings
import com.vasilevyuv.auth.security.JwtTokenService
import com.vasilevyuv.auth.service.AuthService
import com.vasilevyuv.infrastructure.database.DatabaseConnection
import com.vasilevyuv.infrastructure.database.DatabaseSettings
import com.vasilevyuv.notifications.api.notificationRoutes
import com.vasilevyuv.notifications.service.TaskNotificationHub
import com.vasilevyuv.tasks.api.taskRoutes
import com.vasilevyuv.tasks.data.PostgresTaskRepository
import com.vasilevyuv.tasks.service.TaskService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking

fun Application.module() {
    val tokens = JwtTokenService(JwtSettings.from(environment.config))
    val admin = AdminBootstrapSettings.from(environment.config)
    val connection = DatabaseConnection.open(DatabaseSettings.from(environment.config))
    try {
        initializeDatabaseSchema(connection.database)
        configureSerialization()
        configureErrors()
        configureRequestLogging()
        configureWebSockets()

        val users = PostgresUserRepository(connection.database)
        val authService = AuthService(users, BCryptPasswordHasher())
        if (admin != null) {
            runBlocking { authService.bootstrapAdmin(admin.login, admin.password) }
        }
        val notifications = TaskNotificationHub()
        val taskService = TaskService(PostgresTaskRepository(connection.database), notifications)
        configureAuthentication(tokens, users)

        routing {
            get("/") {
                call.respond(mapOf("name" to "Task API", "checkpoint" to "KT4"))
            }
            authRoutes(authService, tokens, JWT_AUTH)
            taskRoutes(taskService, JWT_AUTH)
            notificationRoutes(notifications, users, JWT_AUTH)
            documentationRoutes()
        }
        monitor.subscribe(ApplicationStopped) { connection.close() }
        monitor.subscribe(ApplicationStopping) { notifications.close() }
    } catch (cause: Exception) {
        connection.close()
        throw cause
    }
}
