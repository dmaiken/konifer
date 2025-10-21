package io.asset.repository

import io.database.configureJOOQ
import io.database.migrateSchema
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.HOST
import io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD
import io.r2dbc.spi.ConnectionFactoryOptions.PORT
import io.r2dbc.spi.ConnectionFactoryOptions.USER
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class PostgresAssetRepositoryTest : AssetRepositoryTest() {
    companion object {
        @JvmStatic
        @Container
        private val postgres = PostgreSQLContainer("postgres:17-alpine")

        @JvmStatic
        @BeforeAll
        fun startContainer() {
            postgres.start()
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            postgres.stop()
        }
    }

    @BeforeEach
    fun clearTables() {
        postgres.execInContainer(
            "psql",
            "-U",
            postgres.username,
            "-d",
            postgres.databaseName,
            "-c",
            "TRUNCATE TABLE asset_tree, asset_variant;",
        )
    }

    override fun createRepository(): AssetRepository {
        val options =
            ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql")
                .option(HOST, postgres.host)
                .option(PORT, postgres.getMappedPort(5432))
                .option(USER, postgres.username)
                .option(PASSWORD, postgres.password)
                .option(DATABASE, postgres.databaseName)
                .build()

        val connectionFactory = ConnectionFactories.get(options)
        migrateSchema(connectionFactory)
        return PostgresAssetRepository(
            dslContext = configureJOOQ(connectionFactory),
        )
    }
}
