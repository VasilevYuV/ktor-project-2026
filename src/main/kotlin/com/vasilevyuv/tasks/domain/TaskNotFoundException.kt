package com.vasilevyuv.tasks.domain

class TaskNotFoundException(id: Long) : RuntimeException() {
    override val message: String = "Task $id was not found"
}
