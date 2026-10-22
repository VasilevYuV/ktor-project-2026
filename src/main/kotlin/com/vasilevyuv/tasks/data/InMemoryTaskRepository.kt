package com.vasilevyuv.tasks.data

import com.vasilevyuv.tasks.domain.Task
import com.vasilevyuv.tasks.domain.TaskRepository
import com.vasilevyuv.tasks.domain.TaskStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class InMemoryTaskRepository : TaskRepository {
    private val tasks = ConcurrentHashMap<Long, Task>()
    private val nextId = AtomicLong(1)

    override fun findAll(status: TaskStatus?, query: String?): List<Task> =
        tasks.values
            .filter { status == null || it.status == status }
            .filter { query == null || it.title.contains(query, ignoreCase = true) }
            .sortedBy { it.id }

    override fun findById(id: Long): Task? = tasks[id]

    override fun create(title: String, description: String, status: TaskStatus): Task {
        val task = Task(nextId.getAndIncrement(), title, description, status)
        tasks[task.id] = task
        return task
    }

    override fun delete(id: Long): Boolean = tasks.remove(id) != null
}
