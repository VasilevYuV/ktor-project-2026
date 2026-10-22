package com.vasilevyuv.auth.data

import com.vasilevyuv.auth.domain.UserRole
import org.jetbrains.exposed.v1.core.Table

internal object UsersTable : Table("users") {
    val id = long("id").autoIncrement()
    val login = varchar("login", 32).uniqueIndex()
    val passwordHash = varchar("password_hash", 60)
    val role = enumerationByName<UserRole>("role", 5).default(UserRole.USER)

    override val primaryKey = PrimaryKey(id)
}
