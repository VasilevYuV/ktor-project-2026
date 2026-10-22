package com.vasilevyuv.auth.domain

interface UserRepository {
    suspend fun findById(id: Long): User?
    suspend fun findByLogin(login: String): User?

    /** Returns null when the login is already taken. */
    suspend fun create(login: String, passwordHash: String): User?
}
