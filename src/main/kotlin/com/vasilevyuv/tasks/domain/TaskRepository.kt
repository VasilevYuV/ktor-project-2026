package com.vasilevyuv.tasks.domain

interface TaskRepository {
    suspend fun findAll(status: TaskStatus?, query: String?): List<Task>
    suspend fun findById(id: Long): Task?
    suspend fun create(title: String, description: String, status: TaskStatus): Task
    suspend fun update(id: Long, title: String, description: String, status: TaskStatus): Task?
    suspend fun delete(id: Long): Boolean
}
