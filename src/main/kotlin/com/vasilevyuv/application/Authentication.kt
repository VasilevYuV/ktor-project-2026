package com.vasilevyuv.application

import com.vasilevyuv.auth.domain.UserRepository
import com.vasilevyuv.auth.security.JwtTokenService
import com.vasilevyuv.common.api.ApiError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

const val JWT_AUTH = "auth-jwt"

fun Application.configureAuthentication(tokens: JwtTokenService, users: UserRepository) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "Task API"
            verifier(tokens.verifier)
            validate { credential ->
                val payload = credential.payload
                val userId = payload.getClaim("userId").asString()?.toLongOrNull()
                if (payload.expiresAt != null && userId != null && userId > 0 && users.findById(userId) != null) {
                    JWTPrincipal(payload)
                } else {
                    null
                }
            }
            challenge { scheme, realm ->
                call.response.headers.append(HttpHeaders.WWWAuthenticate, "$scheme realm=\"$realm\"")
                call.respond(HttpStatusCode.Unauthorized, ApiError("UNAUTHORIZED", "A valid Bearer token is required"))
            }
        }
    }
}
