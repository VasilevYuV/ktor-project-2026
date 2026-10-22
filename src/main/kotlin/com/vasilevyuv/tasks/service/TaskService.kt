package com.vasilevyuv.tasks.service

import com.vasilevyuv.common.domain.ValidationException
import com.vasilevyuv.tasks.domain.Task
import com.vasilevyuv.tasks.domain.TaskNotFoundException
import com.vasilevyuv.tasks.domain.TaskRepository
import com.vasilevyuv.tasks.domain.TaskStatus

class TaskService(private val repository: TaskRepository) {
    fun list(status: TaskStatus?, query: String?): List<Task> {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedQuery != null && normalizedQuery.length > MAX_TITLE_LENGTH) {
            throw ValidationException("q must be at most $MAX_TITLE_LENGTH characters")
        }
        return repository.findAll(status, normalizedQuery)
    }

    fun get(id: Long): Task = repository.findById(id) ?: throw TaskNotFoundException(id)

    fun create(title: String, description: String, status: TaskStatus): Task {
        val normalizedTitle = title.trim()
        val normalizedDescription = description.trim()

        if (normalizedTitle.isEmpty() || normalizedTitle.length > MAX_TITLE_LENGTH) {
            throw ValidationException("title must contain 1 to $MAX_TITLE_LENGTH characters")
        }
        if (normalizedDescription.length > MAX_DESCRIPTION_LENGTH) {
            throw ValidationException("description must be at most $MAX_DESCRIPTION_LENGTH characters")
        }

        return repository.create(normalizedTitle, normalizedDescription, status)
    }

    fun delete(id: Long) {
        if (!repository.delete(id)) throw TaskNotFoundException(id)
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_DESCRIPTION_LENGTH = 2_000
    }
}
