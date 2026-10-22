package com.vasilevyuv.auth.domain

class User(val id: Long, val login: String, val passwordHash: String, val role: UserRole)
