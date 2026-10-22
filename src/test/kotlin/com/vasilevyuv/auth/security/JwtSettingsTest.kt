package com.vasilevyuv.auth.security

import com.vasilevyuv.support.TEST_JWT_AUDIENCE
import com.vasilevyuv.support.TEST_JWT_ISSUER
import com.vasilevyuv.support.TEST_JWT_SECRET
import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtSettingsTest {
    @Test
    fun `JWT settings reject weak secrets and invalid lifetime or identity`() {
        for (secret in listOf("", " ".repeat(32), "short", "a".repeat(31))) {
            assertFailsWith<IllegalArgumentException> { settings(secret = secret) }
        }
        for (lifetime in listOf(0L, -1L, 86_401L, Long.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { settings(lifetime = lifetime) }
        }
        assertFailsWith<IllegalArgumentException> { settings(issuer = " ") }
        assertFailsWith<IllegalArgumentException> { settings(audience = "") }
    }

    @Test
    fun `JWT settings require an explicit secret in application configuration`() {
        assertFailsWith<Exception> { JwtSettings.from(MapApplicationConfig()) }
    }

    @Test
    fun `JWT lifetime boundaries are accepted`() {
        assertEquals(1, settings(lifetime = 1).expiresInSeconds)
        assertEquals(86_400, settings(lifetime = 86_400).expiresInSeconds)
    }

    private fun settings(
        secret: String = TEST_JWT_SECRET,
        issuer: String = TEST_JWT_ISSUER,
        audience: String = TEST_JWT_AUDIENCE,
        lifetime: Long = 3600,
    ) = JwtSettings(secret, issuer, audience, lifetime)
}
