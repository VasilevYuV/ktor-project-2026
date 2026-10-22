package com.vasilevyuv.tasks.service

import com.vasilevyuv.tasks.domain.Task
import com.vasilevyuv.tasks.domain.TaskChange
import com.vasilevyuv.tasks.domain.TaskChangePublisher
import com.vasilevyuv.tasks.domain.TaskRepository
import com.vasilevyuv.tasks.domain.TaskStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaskNotificationFailureTest {
    @Test
    fun `storage failures never publish successful task changes`() = runBlocking {
        val changes = mutableListOf<TaskChange>()
        val service = TaskService(FailingRepository(), TaskChangePublisher { changes.add(it) })
        assertFailsWith<StorageFailure> { service.create("Task", "", TaskStatus.TODO) }
        assertFailsWith<StorageFailure> { service.update(1, "Task", "", TaskStatus.DONE) }
        assertFailsWith<StorageFailure> { service.delete(1) }
        assertTrue(changes.isEmpty())
    }

    private class StorageFailure : RuntimeException("Storage is unavailable")

    private class FailingRepository : TaskRepository {
        override suspend fun findAll(status: TaskStatus?, query: String?): List<Task> = throw StorageFailure()
        override suspend fun findById(id: Long): Task? = throw StorageFailure()
        override suspend fun create(title: String, description: String, status: TaskStatus): Task = throw StorageFailure()
        override suspend fun update(id: Long, title: String, description: String, status: TaskStatus): Task? = throw StorageFailure()
        override suspend fun delete(id: Long): Boolean = throw StorageFailure()
    }
}
