package com.vasilevyuv.notifications.service

import com.vasilevyuv.tasks.domain.TaskChange
import com.vasilevyuv.tasks.domain.TaskChangePublisher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.coroutines.cancellation.CancellationException

/** One hub per application. Each connection receives its own bounded queue; there is no replay. */
class TaskNotificationHub(private val bufferCapacity: Int = 64) : TaskChangePublisher, AutoCloseable {
    private val subscribers = mutableSetOf<Channel<TaskChange>>()
    private var closed = false

    init { require(bufferCapacity > 0) }

    @Synchronized
    fun subscribe(): Subscription {
        check(!closed) { "Notification hub is closed" }
        val channel = Channel<TaskChange>(bufferCapacity)
        subscribers.add(channel)
        return Subscription(channel) { unsubscribe(channel) }
    }

    @Synchronized
    override fun publish(change: TaskChange) {
        val iterator = subscribers.iterator()
        while (iterator.hasNext()) {
            val channel = iterator.next()
            if (channel.trySend(change).isFailure) {
                iterator.remove()
                channel.cancel(SlowSubscriberException())
            }
        }
    }

    @Synchronized
    private fun unsubscribe(channel: Channel<TaskChange>) {
        subscribers.remove(channel)
        channel.cancel()
    }

    @Synchronized
    override fun close() {
        closed = true
        subscribers.forEach { it.close() }
        subscribers.clear()
    }

    class Subscription internal constructor(
        val events: ReceiveChannel<TaskChange>,
        private val unsubscribe: () -> Unit,
    ) : AutoCloseable {
        override fun close() = unsubscribe()
    }
}

class SlowSubscriberException : CancellationException("Notification queue is full")
