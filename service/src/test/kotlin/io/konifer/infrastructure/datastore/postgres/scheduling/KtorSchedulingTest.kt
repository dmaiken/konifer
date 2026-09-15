package io.konifer.infrastructure.datastore.postgres.scheduling

import io.konifer.infrastructure.datastore.postgres.PostgresContainerizedTest
import io.konifer.infrastructure.datastore.postgres.PostgresProperties
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class KtorSchedulingTest : PostgresContainerizedTest() {
    @Test
    fun `applies all postgres properties to jdbc datasource`() {
        val properties =
            PostgresProperties(
                database = postgres.databaseName,
                user = postgres.username,
                host = postgres.host,
                port = postgres.getMappedPort(5432),
                password = postgres.password,
                sslMode = "disable",
            )

        jdbcPostgresDatasource(properties).use { hikariDataSource ->
            val postgresDataSource = hikariDataSource.dataSource as PGSimpleDataSource

            postgresDataSource.databaseName shouldBe properties.database
            postgresDataSource.user shouldBe properties.user
            postgresDataSource.serverNames.toList() shouldBe listOf(properties.host)
            postgresDataSource.portNumbers.toList() shouldBe listOf(properties.port)
            postgresDataSource.password shouldBe properties.password
            postgresDataSource.sslMode shouldBe properties.sslMode
        }
    }
}
