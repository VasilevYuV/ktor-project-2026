package com.vasilevyuv.tasks.domain

enum class TaskChangeType { CREATED, UPDATED, DELETED }

data class TaskChange(val type: TaskChangeType, val taskId: Long)

fun interface TaskChangePublisher {
    /** Publishes a committed change without waiting for network clients. */
    fun publish(change: TaskChange)
}
