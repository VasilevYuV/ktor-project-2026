package com.vasilevyuv.notifications.service

import com.vasilevyuv.tasks.domain.TaskChange
import com.vasilevyuv.tasks.domain.TaskChangeType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaskNotificationHubTest {
    @Test
    fun `every subscriber receives changes in publication order without replay`() = runBlocking {
        TaskNotificationHub().use { hub ->
            hub.publish(TaskChange(TaskChangeType.CREATED, 1))
            hub.subscribe().use { first ->
                hub.subscribe().use { second ->
                    val changes = listOf(TaskChange(TaskChangeType.UPDATED, 1), TaskChange(TaskChangeType.DELETED, 1))
                    changes.forEach(hub::publish)
                    for (change in changes) {
                        assertEquals(change, first.events.receive())
                        assertEquals(change, second.events.receive())
                    }
                    assertTrue(first.events.tryReceive().isFailure)
                }
            }
        }
    }

    @Test
    fun `disconnect removes the queue without affecting another subscriber`() = runBlocking {
        TaskNotificationHub().use { hub ->
            val disconnected = hub.subscribe()
            hub.subscribe().use { active ->
                disconnected.close()
                disconnected.close()
                val change = TaskChange(TaskChangeType.CREATED, 1)
                hub.publish(change)
                assertTrue(disconnected.events.receiveCatching().isClosed)
                assertEquals(change, active.events.receive())
            }
        }
    }

    @Test
    fun `slow subscriber is removed while an active subscriber keeps receiving`() = runBlocking<Unit> {
        TaskNotificationHub(bufferCapacity = 1).use { hub ->
            hub.subscribe().use { slow ->
                hub.subscribe().use { active ->
                    for (id in 1L..3L) {
                        val change = TaskChange(TaskChangeType.CREATED, id)
                        hub.publish(change)
                        assertEquals(change, active.events.receive())
                    }
                    assertFailsWith<SlowSubscriberException> { slow.events.receive() }
                }
            }
        }
    }

    @Test
    fun `shutdown completes subscriptions and publishing without subscribers is harmless`() = runBlocking {
        val hub = TaskNotificationHub()
        val subscription = hub.subscribe()
        hub.close()
        assertFailsWith<IllegalStateException> { hub.subscribe() }
        assertTrue(subscription.events.receiveCatching().isClosed)
        hub.publish(TaskChange(TaskChangeType.CREATED, 1))
        subscription.close()
    }
}
