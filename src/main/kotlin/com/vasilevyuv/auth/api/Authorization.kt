package com.vasilevyuv.auth.api

import com.vasilevyuv.auth.domain.ForbiddenException
import com.vasilevyuv.auth.domain.UserRole
import com.vasilevyuv.auth.security.AuthenticatedUser
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

fun ApplicationCall.authenticatedUser(): AuthenticatedUser =
    checkNotNull(principal<AuthenticatedUser>()) { "This route must be inside authenticate" }

fun ApplicationCall.requireAdmin() {
    if (authenticatedUser().role != UserRole.ADMIN) throw ForbiddenException()
}
