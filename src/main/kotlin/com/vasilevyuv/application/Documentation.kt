package com.vasilevyuv.application

import io.ktor.http.ContentType
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get

fun Route.documentationRoutes() {
    val specification = checkNotNull(application.javaClass.classLoader.getResource("openapi/task-api.yaml")) {
        "OpenAPI specification is missing from application resources"
    }.readText()

    get("/openapi.yaml") {
        call.respondText(specification, ContentType.Application.Yaml)
    }
    staticResources("/docs", "documentation")
    staticResources("/docs/assets", "META-INF/resources/webjars")
}
