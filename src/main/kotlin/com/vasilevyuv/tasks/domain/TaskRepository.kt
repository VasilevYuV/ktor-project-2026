package com.vasilevyuv.tasks.domain

interface TaskRepository {
    fun findAll(status: TaskStatus?, query: String?): List<Task>
    fun findById(id: Long): Task?
    fun create(title: String, description: String, status: TaskStatus): Task
    fun delete(id: Long): Boolean
}
