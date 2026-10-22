package com.vasilevyuv.auth.security

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AdminBootstrapSettingsTest {
    @Test
    fun `administrator creation is disabled when both settings are absent or empty`() {
        assertNull(AdminBootstrapSettings.from(MapApplicationConfig()))
        assertNull(AdminBootstrapSettings.from(MapApplicationConfig("admin.login" to "", "admin.password" to "")))
    }

    @Test
    fun `both administrator settings must be supplied together`() {
        for (config in listOf(
            MapApplicationConfig("admin.login" to "administrator"),
            MapApplicationConfig("admin.password" to "Test password 123!"),
        )) {
            assertFailsWith<IllegalArgumentException> { AdminBootstrapSettings.from(config) }
        }
    }
}
