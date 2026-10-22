package com.vasilevyuv.notifications.api

import com.vasilevyuv.auth.api.authenticatedUser
import com.vasilevyuv.auth.domain.UserRepository
import com.vasilevyuv.notifications.service.SlowSubscriberException
import com.vasilevyuv.notifications.service.TaskNotificationHub
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

fun Route.notificationRoutes(hub: TaskNotificationHub, users: UserRepository, authenticationProvider: String) {
    authenticate(authenticationProvider) {
        webSocket("/ws/tasks") {
            val user = call.authenticatedUser()
            val subscription = hub.subscribe()
            try {
                // The queue is attached before READY, so changes after READY cannot miss the subscription.
                send(Json.encodeToString(TaskNotification("READY")))
                val sender = launch {
                    try {
                        for (change in subscription.events) {
                            if (Instant.now() >= user.expiresAt || users.findById(user.id) == null) {
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication expired"))
                                return@launch
                            }
                            withTimeout(5_000) { send(Json.encodeToString(change.toNotification())) }
                        }
                        close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server is stopping"))
                    } catch (_: SlowSubscriberException) {
                        close(CloseReason(1013, "Client is too slow; reconnect and reload tasks"))
                    } catch (_: TimeoutCancellationException) {
                        close(CloseReason(1013, "Client is too slow; reconnect and reload tasks"))
                    }
                }
                val expiry = launch {
                    delay(Duration.between(Instant.now(), user.expiresAt).toMillis().coerceAtLeast(0))
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Token expired; log in again"))
                }
                try {
                    // This endpoint only sends notifications. Mutations use the existing HTTP API and its permissions.
                    for (frame in incoming) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Use HTTP to change tasks"))
                        break
                    }
                } finally {
                    sender.cancelAndJoin()
                    expiry.cancelAndJoin()
                }
            } finally {
                subscription.close()
            }
        }
    }
}
