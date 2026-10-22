package com.vasilevyuv.application

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.websocket.ChannelOverflow
import kotlin.time.Duration.Companion.seconds

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = 4096
        masking = false
        channels {
            incoming = bounded(8, ChannelOverflow.CLOSE)
            outgoing = bounded(16)
        }
    }
}
