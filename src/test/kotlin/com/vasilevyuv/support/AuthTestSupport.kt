package com.vasilevyuv.support

import com.vasilevyuv.auth.api.LoginRequest
import com.vasilevyuv.auth.api.RegisterRequest
import com.vasilevyuv.auth.api.TokenResponse
import com.vasilevyuv.common.api.ApiError
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// These values belong only to disposable tests, never to the running application.
const val TEST_JWT_SECRET = "test-only-secret-at-least-32-bytes-long"
const val TEST_JWT_ISSUER = "test-task-api"
const val TEST_JWT_AUDIENCE = "test-task-client"
const val TEST_PASSWORD = "Test password 123!"
const val TEST_ADMIN_LOGIN = "admin_tester"
val TEST_ADMIN_SETTINGS = mapOf("admin.login" to TEST_ADMIN_LOGIN, "admin.password" to TEST_PASSWORD)

suspend fun HttpClient.register(login: String = "student", password: String = TEST_PASSWORD): HttpResponse =
    post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(RegisterRequest(login, password)))
    }

suspend fun HttpClient.login(login: String = "student", password: String = TEST_PASSWORD): HttpResponse =
    post("/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(LoginRequest(login, password)))
    }

suspend fun HttpResponse.token(): TokenResponse = Json.decodeFromString(bodyAsText())

suspend fun HttpResponse.assertError(expectedStatus: HttpStatusCode, expectedCode: String) {
    assertEquals(expectedStatus, status, bodyAsText())
    assertEquals(ContentType.Application.Json, contentType()?.withoutParameters())
    val error = Json.decodeFromString<ApiError>(bodyAsText())
    assertEquals(expectedCode, error.code)
    assertTrue(error.message.isNotBlank())
}
