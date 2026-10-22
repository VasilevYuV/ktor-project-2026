package com.vasilevyuv.auth.security

import io.ktor.server.config.ApplicationConfig

class JwtSettings(val secret: String, val issuer: String, val audience: String, val expiresInSeconds: Long) {
    init {
        require(secret.isNotBlank() && secret.toByteArray(Charsets.UTF_8).size >= 32) {
            "JWT_SECRET must contain at least 32 UTF-8 bytes"
        }
        require(issuer.isNotBlank()) { "JWT_ISSUER must not be blank" }
        require(audience.isNotBlank()) { "JWT_AUDIENCE must not be blank" }
        require(expiresInSeconds in 1..86_400) { "JWT_EXPIRES_IN_SECONDS must be between 1 and 86400" }
    }

    companion object {
        fun from(config: ApplicationConfig) = JwtSettings(
            secret = config.property("jwt.secret").getString(),
            issuer = config.property("jwt.issuer").getString(),
            audience = config.property("jwt.audience").getString(),
            expiresInSeconds = config.property("jwt.expiresInSeconds").getString().toLong(),
        )
    }
}
