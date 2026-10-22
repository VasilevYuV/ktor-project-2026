package com.vasilevyuv.auth.domain

class LoginAlreadyTakenException : RuntimeException("Login is already taken")

class InvalidCredentialsException : RuntimeException("Invalid login or password")

class ForbiddenException : RuntimeException("Administrator role is required")
