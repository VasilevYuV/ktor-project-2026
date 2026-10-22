package com.vasilevyuv.auth.api

import com.vasilevyuv.auth.security.JwtTokenService
import com.vasilevyuv.auth.service.AuthService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(service: AuthService, tokens: JwtTokenService, authenticationProvider: String) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val user = service.register(request.login, request.password)
            call.respond(HttpStatusCode.Created, UserResponse(user.id, user.login, user.role.name))
        }
        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = service.login(request.login, request.password)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(TokenResponse(tokens.issue(user.id), tokens.expiresInSeconds))
        }
        authenticate(authenticationProvider) {
            get("/me") {
                val user = call.authenticatedUser()
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                call.respond(UserResponse(user.id, user.login, user.role.name))
            }
        }
    }
}
