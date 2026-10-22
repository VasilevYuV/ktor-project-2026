package com.vasilevyuv.tasks.service

import com.vasilevyuv.common.domain.ValidationException
import com.vasilevyuv.tasks.domain.Task
import com.vasilevyuv.tasks.domain.TaskNotFoundException
import com.vasilevyuv.tasks.domain.TaskRepository
import com.vasilevyuv.tasks.domain.TaskStatus

class TaskService(private val repository: TaskRepository) {
    suspend fun list(status: TaskStatus?, query: String?): List<Task> {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedQuery != null && normalizedQuery.length > MAX_TITLE_LENGTH) {
            throw ValidationException("q must be at most $MAX_TITLE_LENGTH characters")
        }
        return repository.findAll(status, normalizedQuery)
    }

    suspend fun get(id: Long): Task = repository.findById(id) ?: throw TaskNotFoundException(id)

    suspend fun create(title: String, description: String, status: TaskStatus): Task {
        val normalizedTitle = title.trim()
        val normalizedDescription = description.trim()
        validateFields(normalizedTitle, normalizedDescription)
        return repository.create(normalizedTitle, normalizedDescription, status)
    }

    suspend fun update(id: Long, title: String, description: String, status: TaskStatus): Task {
        val normalizedTitle = title.trim()
        val normalizedDescription = description.trim()
        validateFields(normalizedTitle, normalizedDescription)
        return repository.update(id, normalizedTitle, normalizedDescription, status)
            ?: throw TaskNotFoundException(id)
    }

    suspend fun delete(id: Long) {
        if (!repository.delete(id)) throw TaskNotFoundException(id)
    }

    private fun validateFields(title: String, description: String) {
        if (title.isEmpty() || title.length > MAX_TITLE_LENGTH) {
            throw ValidationException("title must contain 1 to $MAX_TITLE_LENGTH characters")
        }
        if (description.length > MAX_DESCRIPTION_LENGTH) {
            throw ValidationException("description must be at most $MAX_DESCRIPTION_LENGTH characters")
        }
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_DESCRIPTION_LENGTH = 2_000
    }
}
