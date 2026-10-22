package com.vasilevyuv.auth.domain

interface PasswordHasher {
    suspend fun hash(password: String): String

    /** A missing hash still runs a password check to avoid a fast path for unknown users. */
    suspend fun verify(password: String, hash: String?): Boolean
}
