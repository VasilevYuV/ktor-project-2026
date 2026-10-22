package com.vasilevyuv.auth.api

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val login: String, val password: String)

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class UserResponse(val id: Long, val login: String)

@Serializable
data class TokenResponse(val accessToken: String, val expiresIn: Long, val tokenType: String = "Bearer")
