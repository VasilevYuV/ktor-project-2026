package com.vasilevyuv.notifications.api

import com.vasilevyuv.tasks.domain.TaskChange
import kotlinx.serialization.Serializable

@Serializable
data class TaskNotification(val type: String, val taskId: Long? = null)

fun TaskChange.toNotification() = TaskNotification(type = "TASK_${type.name}", taskId = taskId)
