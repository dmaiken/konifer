package io.konifer.infrastructure.datastore.postgres

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import org.junit.jupiter.api.Test
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE as R2DBC_DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.HOST as R2DBC_HOST
import io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD as R2DBC_PASSWORD
import io.r2dbc.spi.ConnectionFactoryOptions.PORT as R2DBC_PORT
import io.r2dbc.spi.ConnectionFactoryOptions.USER as R2DBC_USER

class KtorPostgresTest {
    @Test
    fun `applies all postgres properties to r2dbc connection factory`() {
        val properties =
            PostgresProperties(
                database = "databaseName",
                user = "username",
                host = "host",
                port = 1234,
                password = "password",
                sslMode = "disable",
            )
        val expectedConnectionFactory = mockk<ConnectionFactory>()
        val capturedOptions = slot<ConnectionFactoryOptions>()

        mockkStatic(ConnectionFactories::class)
        try {
            every { ConnectionFactories.get(capture(capturedOptions)) } returns expectedConnectionFactory

            connectToPostgres(properties) shouldBe expectedConnectionFactory

            capturedOptions.captured.getValue(R2DBC_DATABASE) shouldBe properties.database
            capturedOptions.captured.getValue(R2DBC_USER) shouldBe properties.user
            capturedOptions.captured.getValue(R2DBC_HOST) shouldBe properties.host
            capturedOptions.captured.getValue(R2DBC_PORT) shouldBe properties.port
            capturedOptions.captured.getValue(R2DBC_PASSWORD) shouldBe properties.password
            capturedOptions.captured.getValue(Option.valueOf<String>("sslMode")) shouldBe properties.sslMode
        } finally {
            unmockkStatic(ConnectionFactories::class)
        }
    }
}
