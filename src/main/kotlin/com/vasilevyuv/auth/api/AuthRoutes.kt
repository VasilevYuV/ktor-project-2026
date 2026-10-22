package com.vasilevyuv.auth.api

import com.vasilevyuv.auth.security.JwtTokenService
import com.vasilevyuv.auth.service.AuthService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(service: AuthService, tokens: JwtTokenService) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val user = service.register(request.login, request.password)
            call.respond(HttpStatusCode.Created, UserResponse(user.id, user.login))
        }
        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = service.login(request.login, request.password)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(TokenResponse(tokens.issue(user.id), tokens.expiresInSeconds))
        }
    }
}
