package com.vasilevyuv.tasks.domain

data class Task(
    val id: Long,
    val title: String,
    val description: String,
    val status: TaskStatus,
)

enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
}
