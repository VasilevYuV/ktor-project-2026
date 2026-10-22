package com.vasilevyuv.application

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun Application.configureRequestLogging() {
    install(CallLogging) {
        logger = LoggerFactory.getLogger("http.requests")
        level = Level.INFO
        disableDefaultColors()
        format { call ->
            // Headers, query parameters and bodies may contain credentials.
            val path = call.request.path().replace('\r', '_').replace('\n', '_')
            "method=${call.request.httpMethod.value} path=$path " +
                "status=${call.response.status()?.value} durationMs=${call.processingTimeMillis()}"
        }
    }
}
