package com.vasilevyuv.tasks.data

import com.vasilevyuv.tasks.domain.Task
import com.vasilevyuv.tasks.domain.TaskRepository
import com.vasilevyuv.tasks.domain.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class PostgresTaskRepository(private val database: Database) : TaskRepository {
    override suspend fun findAll(status: TaskStatus?, query: String?): List<Task> = dbQuery {
        val selection = TasksTable.selectAll()
        if (status != null) {
            selection.andWhere { TasksTable.status eq status }
        }
        if (query != null) {
            // Treat %, _ and backslash as literal search text, not SQL wildcards.
            val escapedQuery = query.lowercase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            selection.andWhere { TasksTable.title.lowerCase() like "%$escapedQuery%" }
        }
        selection.orderBy(TasksTable.id to SortOrder.ASC).map { it.toTask() }
    }

    override suspend fun findById(id: Long): Task? = dbQuery {
        TasksTable.selectAll().where { TasksTable.id eq id }.singleOrNull()?.toTask()
    }

    override suspend fun create(title: String, description: String, status: TaskStatus): Task = dbQuery {
        val id = TasksTable.insert {
            it[TasksTable.title] = title
            it[TasksTable.description] = description
            it[TasksTable.status] = status
        }[TasksTable.id]
        Task(id, title, description, status)
    }

    override suspend fun update(id: Long, title: String, description: String, status: TaskStatus): Task? = dbQuery {
        val updatedRows = TasksTable.update({ TasksTable.id eq id }) {
            it[TasksTable.title] = title
            it[TasksTable.description] = description
            it[TasksTable.status] = status
        }
        if (updatedRows == 0) null else Task(id, title, description, status)
    }

    override suspend fun delete(id: Long): Boolean = dbQuery {
        TasksTable.deleteWhere { TasksTable.id eq id } > 0
    }

    private suspend fun <T> dbQuery(block: () -> T): T = withContext(Dispatchers.IO) {
        transaction(database) { block() }
    }

    private fun ResultRow.toTask(): Task = Task(
        id = this[TasksTable.id],
        title = this[TasksTable.title],
        description = this[TasksTable.description],
        status = this[TasksTable.status],
    )
}
