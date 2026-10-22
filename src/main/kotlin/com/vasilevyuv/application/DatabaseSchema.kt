package com.vasilevyuv.application

import com.vasilevyuv.auth.data.UsersTable
import com.vasilevyuv.tasks.data.TasksTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun initializeDatabaseSchema(database: Database) {
    val roleMigration = checkNotNull(Database::class.java.classLoader.getResource("db/migration/V1__add_user_roles.sql")) {
        "User role migration is missing from application resources"
    }.readText()
    transaction(database) {
        // Serialize schema initialization when several application instances start together.
        exec("SELECT pg_advisory_xact_lock(2026, 1)")
        SchemaUtils.create(TasksTable, UsersTable)
        exec(roleMigration)
    }
}
