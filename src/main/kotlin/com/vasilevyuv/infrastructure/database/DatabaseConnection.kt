package com.vasilevyuv.infrastructure.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

class DatabaseConnection private constructor(
    val database: Database,
    private val dataSource: HikariDataSource,
) : AutoCloseable {
    override fun close() {
        TransactionManager.closeAndUnregister(database)
        dataSource.close()
    }

    companion object {
        fun open(settings: DatabaseSettings): DatabaseConnection {
            val dataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = settings.url
                username = settings.user
                password = settings.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = settings.poolSize
                minimumIdle = 1
                connectionTimeout = 5_000
                validationTimeout = 2_000
                addDataSourceProperty("connectTimeout", "5")
                addDataSourceProperty("socketTimeout", "30")
            })

            try {
                return DatabaseConnection(Database.connect(dataSource), dataSource)
            } catch (cause: Exception) {
                dataSource.close()
                throw cause
            }
        }
    }
}
