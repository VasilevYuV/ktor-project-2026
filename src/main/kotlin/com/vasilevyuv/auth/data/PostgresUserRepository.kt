package com.vasilevyuv.auth.data

import com.vasilevyuv.auth.domain.User
import com.vasilevyuv.auth.domain.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PostgresUserRepository(private val database: Database) : UserRepository {
    override suspend fun findById(id: Long): User? = dbQuery {
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toUser()
    }

    override suspend fun findByLogin(login: String): User? = dbQuery {
        UsersTable.selectAll().where { UsersTable.login eq login }.singleOrNull()?.toUser()
    }

    override suspend fun create(login: String, passwordHash: String): User? = dbQuery {
        // The unique index also prevents duplicate logins during concurrent registrations.
        val inserted = UsersTable.insertIgnore {
            it[UsersTable.login] = login
            it[UsersTable.passwordHash] = passwordHash
        }
        if (inserted.insertedCount == 0) null else User(inserted[UsersTable.id], login, passwordHash)
    }

    private suspend fun <T> dbQuery(block: () -> T): T = withContext(Dispatchers.IO) {
        transaction(database) { block() }
    }

    private fun ResultRow.toUser() = User(
        id = this[UsersTable.id],
        login = this[UsersTable.login],
        passwordHash = this[UsersTable.passwordHash],
    )
}
