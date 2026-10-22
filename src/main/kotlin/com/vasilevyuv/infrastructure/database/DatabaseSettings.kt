package com.vasilevyuv.infrastructure.database

import io.ktor.server.config.ApplicationConfig

class DatabaseSettings(
    val url: String,
    val user: String,
    val password: String,
    val poolSize: Int,
) {
    init {
        require(url.startsWith("jdbc:postgresql://")) { "database.url must be a PostgreSQL JDBC URL" }
        require(user.isNotBlank()) { "database.user must not be blank" }
        require(password.isNotBlank()) { "Set DATABASE_PASSWORD before starting the application" }
        require(poolSize in 1..20) { "database.poolSize must be between 1 and 20" }
    }

    companion object {
        fun from(config: ApplicationConfig): DatabaseSettings = DatabaseSettings(
            url = config.property("database.url").getString(),
            user = config.property("database.user").getString(),
            password = config.property("database.password").getString(),
            poolSize = config.property("database.poolSize").getString().toIntOrNull()
                ?: error("database.poolSize must be an integer"),
        )
    }
}
