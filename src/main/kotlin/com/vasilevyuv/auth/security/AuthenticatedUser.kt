package com.vasilevyuv.auth.security

import com.vasilevyuv.auth.domain.UserRole
import java.time.Instant

/** Built from the database for each authenticated request, not from client-supplied role claims. */
data class AuthenticatedUser(val id: Long, val login: String, val role: UserRole, val expiresAt: Instant)
