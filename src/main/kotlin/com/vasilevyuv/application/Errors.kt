package com.vasilevyuv.application

import com.vasilevyuv.auth.domain.InvalidCredentialsException
import com.vasilevyuv.auth.domain.ForbiddenException
import com.vasilevyuv.auth.domain.LoginAlreadyTakenException
import com.vasilevyuv.common.api.ApiError
import com.vasilevyuv.common.domain.ValidationException
import com.vasilevyuv.tasks.domain.TaskNotFoundException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlin.coroutines.cancellation.CancellationException

fun Application.configureErrors() {
    install(StatusPages) {
        exception<ForbiddenException> { call, _ ->
            call.respond(HttpStatusCode.Forbidden, ApiError("FORBIDDEN", "Administrator role is required"))
        }
        exception<LoginAlreadyTakenException> { call, _ ->
            call.respond(HttpStatusCode.Conflict, ApiError("LOGIN_ALREADY_TAKEN", "Login is already taken"))
        }
        exception<InvalidCredentialsException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, ApiError("INVALID_CREDENTIALS", "Invalid login or password"))
        }
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiError("VALIDATION_ERROR", cause.message))
        }
        exception<TaskNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ApiError("TASK_NOT_FOUND", cause.message))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError("INVALID_REQUEST", "Request body must be valid JSON with the expected fields"),
            )
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                ApiError("UNSUPPORTED_MEDIA_TYPE", "Use Content-Type: application/json"),
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ApiError("ROUTE_NOT_FOUND", "Route was not found"))
        }
        exception<Exception> { call, cause ->
            if (cause is CancellationException) throw cause

            call.application.log.error("Unhandled request error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError("INTERNAL_ERROR", "An unexpected error occurred"),
            )
        }
    }
}
