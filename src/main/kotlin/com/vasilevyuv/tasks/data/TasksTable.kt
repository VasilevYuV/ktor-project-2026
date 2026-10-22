package com.vasilevyuv.tasks.data

import com.vasilevyuv.tasks.domain.TaskStatus
import org.jetbrains.exposed.v1.core.Table

internal object TasksTable : Table("tasks") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 200)
    val description = varchar("description", 2_000).default("")
    val status = enumerationByName<TaskStatus>("status", 11).default(TaskStatus.TODO)

    override val primaryKey = PrimaryKey(id)
}
