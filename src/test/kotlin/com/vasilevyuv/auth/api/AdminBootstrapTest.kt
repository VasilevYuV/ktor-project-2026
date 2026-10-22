package com.vasilevyuv.auth.api

import at.favre.lib.crypto.bcrypt.BCrypt
import com.vasilevyuv.auth.data.PostgresUserRepository
import com.vasilevyuv.auth.domain.UserRole
import com.vasilevyuv.auth.security.BCryptPasswordHasher
import com.vasilevyuv.auth.service.AuthService
import com.vasilevyuv.infrastructure.database.DatabaseConnection
import com.vasilevyuv.support.PostgresApiTest
import com.vasilevyuv.support.TEST_ADMIN_LOGIN
import com.vasilevyuv.support.TEST_ADMIN_SETTINGS
import com.vasilevyuv.support.TEST_PASSWORD
import com.vasilevyuv.support.assertError
import com.vasilevyuv.support.login
import com.vasilevyuv.support.register
import com.vasilevyuv.support.token
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class AdminBootstrapTest : PostgresApiTest() {
    @Test
    fun `startup without administrator settings creates no privileged accounts`() = apiTest {
        assertEquals(0, adminCount())
        assertEquals(HttpStatusCode.Created, client.register().status)
        assertEquals(0, adminCount())
    }

    @Test
    fun `configured administrator has a bcrypt password and cannot be publicly re-registered`() = apiTest(settings = TEST_ADMIN_SETTINGS) {
        val stored = storedUser(TEST_ADMIN_LOGIN)
        assertEquals("ADMIN", stored.role)
        assertTrue(stored.hash.startsWith("\$2a\$12\$"))
        assertTrue(BCrypt.verifyer().verify(TEST_PASSWORD.toCharArray(), stored.hash).verified)
        assertEquals(HttpStatusCode.OK, client.login(TEST_ADMIN_LOGIN).status)
        client.register(TEST_ADMIN_LOGIN).assertError(HttpStatusCode.Conflict, "LOGIN_ALREADY_TAKEN")
        assertEquals(stored, storedUser(TEST_ADMIN_LOGIN))
    }

    @Test
    fun `bootstrap is idempotent and an existing admin works after settings are removed`() {
        lateinit var original: StoredUser
        lateinit var token: String
        apiTest(settings = TEST_ADMIN_SETTINGS) {
            original = storedUser(TEST_ADMIN_LOGIN)
            token = client.login(TEST_ADMIN_LOGIN).token().accessToken
        }
        apiTest(resetData = false, settings = TEST_ADMIN_SETTINGS) {
            assertEquals(original, storedUser(TEST_ADMIN_LOGIN))
            assertEquals(1, adminCount())
        }
        apiTest(resetData = false) {
            assertEquals(original, storedUser(TEST_ADMIN_LOGIN))
            val me = client.get("/auth/me") { bearerAuth(token) }
            assertEquals("ADMIN", Json.decodeFromString<UserResponse>(me.bodyAsText()).role)
            client.delete("/tasks/999") { bearerAuth(token) }.assertError(HttpStatusCode.NotFound, "TASK_NOT_FOUND")
        }
    }

    @Test
    fun `bootstrap refuses to promote an existing ordinary user`() {
        lateinit var original: StoredUser
        apiTest {
            client.register(TEST_ADMIN_LOGIN)
            original = storedUser(TEST_ADMIN_LOGIN)
        }
        assertFailsWith<Exception> {
            apiTest(resetData = false, settings = TEST_ADMIN_SETTINGS) { fail("Startup should have failed") }
        }
        assertEquals(original, storedUser(TEST_ADMIN_LOGIN))
        assertEquals("USER", original.role)
        apiTest(resetData = false) { assertEquals(HttpStatusCode.OK, client.login(TEST_ADMIN_LOGIN).status) }
    }

    @Test
    fun `bootstrap refuses a changed password without overwriting the existing admin`() {
        lateinit var original: StoredUser
        apiTest(settings = TEST_ADMIN_SETTINGS) { original = storedUser(TEST_ADMIN_LOGIN) }
        assertFailsWith<Exception> {
            apiTest(resetData = false, settings = TEST_ADMIN_SETTINGS + ("admin.password" to "Different password 456!")) {
                fail("Startup should have failed")
            }
        }
        assertEquals(original, storedUser(TEST_ADMIN_LOGIN))
        apiTest(resetData = false) {
            assertEquals(HttpStatusCode.OK, client.login(TEST_ADMIN_LOGIN).status)
            client.login(TEST_ADMIN_LOGIN, "Different password 456!").assertError(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
        }
    }

    @Test
    fun `invalid administrator credentials prevent startup and create no admin`() {
        for ((login, password) in listOf("bad login" to TEST_PASSWORD, TEST_ADMIN_LOGIN to "12345678901",
            TEST_ADMIN_LOGIN to "я".repeat(37))) {
            assertFailsWith<Exception> {
                apiTest(settings = mapOf("admin.login" to login, "admin.password" to password)) {
                    fail("Startup should have failed")
                }
            }
            assertEquals(0, adminCount())
        }
    }

    @Test
    fun `concurrent admin initialization creates one account without replacing its password`() = apiTest {
        DatabaseConnection.open(databaseSettings()).use { connection ->
            val service = AuthService(PostgresUserRepository(connection.database), BCryptPasswordHasher())
            val admins = coroutineScope {
                (1..2).map { async { service.bootstrapAdmin(TEST_ADMIN_LOGIN, TEST_PASSWORD) } }.awaitAll()
            }
            assertEquals(1, admins.map { it.id }.toSet().size)
            assertTrue(admins.all { it.role == UserRole.ADMIN })
            assertEquals(1, adminCount())
            val original = storedUser(TEST_ADMIN_LOGIN)
            service.bootstrapAdmin(TEST_ADMIN_LOGIN, TEST_PASSWORD)
            assertEquals(original, storedUser(TEST_ADMIN_LOGIN))
        }
    }

    @Test
    fun `role migration preserves existing users passwords tasks and tokens`() {
        lateinit var original: StoredUser
        lateinit var token: String
        lateinit var taskJson: String
        apiTest {
            client.register()
            original = storedUser("student")
            token = client.login().token().accessToken
            taskJson = client.post("/tasks") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Before role migration","description":"Keep me","status":"DONE"}""")
            }.bodyAsText()
        }
        // Reproduce the previous schema only inside the disposable test database.
        withConnection { connection -> connection.createStatement().use { it.execute("ALTER TABLE users DROP COLUMN role") } }
        apiTest(resetData = false) {
            assertEquals(original, storedUser("student"))
            assertEquals(taskJson, client.get("/tasks/1").bodyAsText())
            val response = client.get("/auth/me") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("USER", Json.decodeFromString<UserResponse>(response.bodyAsText()).role)
            assertEquals(HttpStatusCode.OK, client.login().status)
        }
    }

    private data class StoredUser(val id: Long, val hash: String, val role: String)

    private fun storedUser(login: String): StoredUser = withConnection { connection ->
        connection.prepareStatement("SELECT id, password_hash, role FROM users WHERE login = ?").use { statement ->
            statement.setString(1, login)
            statement.executeQuery().use { rows ->
                assertTrue(rows.next())
                StoredUser(rows.getLong(1), rows.getString(2), rows.getString(3))
            }
        }
    }

    private fun adminCount(): Int = withConnection { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'").use { rows -> rows.next(); rows.getInt(1) }
        }
    }
}
