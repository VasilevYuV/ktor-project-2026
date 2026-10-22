package com.vasilevyuv.tasks.api

import com.vasilevyuv.tasks.domain.Task
import kotlinx.serialization.Serializable

@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String = "",
    val status: String = "TODO",
)

@Serializable
data class UpdateTaskRequest(
    val title: String,
    val description: String,
    val status: String,
)

@Serializable
data class TaskResponse(
    val id: Long,
    val title: String,
    val description: String,
    val status: String,
)

fun Task.toResponse(): TaskResponse = TaskResponse(id, title, description, status.name)
