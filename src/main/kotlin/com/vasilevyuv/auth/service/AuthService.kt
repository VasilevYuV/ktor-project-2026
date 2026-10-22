package com.vasilevyuv.auth.service

import com.vasilevyuv.auth.domain.InvalidCredentialsException
import com.vasilevyuv.auth.domain.LoginAlreadyTakenException
import com.vasilevyuv.auth.domain.PasswordHasher
import com.vasilevyuv.auth.domain.User
import com.vasilevyuv.auth.domain.UserRepository
import com.vasilevyuv.common.domain.ValidationException

class AuthService(private val users: UserRepository, private val passwords: PasswordHasher) {
    suspend fun register(login: String, password: String): User {
        val normalizedLogin = validateCredentials(login, password)
        val hash = passwords.hash(password)
        return users.create(normalizedLogin, hash) ?: throw LoginAlreadyTakenException()
    }

    suspend fun login(login: String, password: String): User {
        val normalizedLogin = validateCredentials(login, password)
        val user = users.findByLogin(normalizedLogin)
        val matches = passwords.verify(password, user?.passwordHash)
        if (user == null || !matches) throw InvalidCredentialsException()
        return user
    }

    private fun validateCredentials(login: String, password: String): String {
        val trimmedLogin = login.trim()
        if (!LOGIN_PATTERN.matches(trimmedLogin)) {
            throw ValidationException("login must contain 3 to 32 Latin letters, digits or underscores")
        }
        if (password.codePointCount(0, password.length) < 8 || password.isBlank() ||
            password.toByteArray(Charsets.UTF_8).size > 72
        ) {
            throw ValidationException("password must contain at least 8 characters and at most 72 UTF-8 bytes")
        }
        return trimmedLogin.lowercase()
    }

    private companion object {
        val LOGIN_PATTERN = Regex("[A-Za-z0-9_]{3,32}")
    }
}
