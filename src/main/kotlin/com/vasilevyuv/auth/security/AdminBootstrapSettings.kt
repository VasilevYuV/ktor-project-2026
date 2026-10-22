package com.vasilevyuv.auth.security

import io.ktor.server.config.ApplicationConfig

class AdminBootstrapSettings private constructor(val login: String, val password: String) {
    companion object {
        fun from(config: ApplicationConfig): AdminBootstrapSettings? {
            val login = config.propertyOrNull("admin.login")?.getString().orEmpty()
            val password = config.propertyOrNull("admin.password")?.getString().orEmpty()
            if (login.isEmpty() && password.isEmpty()) return null
            require(login.isNotEmpty() && password.isNotEmpty()) {
                "Set both ADMIN_LOGIN and ADMIN_PASSWORD, or leave both unset"
            }
            return AdminBootstrapSettings(login, password)
        }
    }
}
