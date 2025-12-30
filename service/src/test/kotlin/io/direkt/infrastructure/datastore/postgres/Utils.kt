package io.direkt.infrastructure.datastore.postgres

import io.direkt.infrastructure.datastore.configureR2dbcJOOQ
import io.direkt.infrastructure.datastore.migrateSchema
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.HOST
import io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD
import io.r2dbc.spi.ConnectionFactoryOptions.PORT
import io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL
import io.r2dbc.spi.ConnectionFactoryOptions.USER
import org.jooq.DSLContext
import org.testcontainers.containers.PostgreSQLContainer

fun postgresContainer(): PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine").withReuse(true)

fun truncateTables(postgres: PostgreSQLContainer<out PostgreSQLContainer<*>>) {
    postgres.execInContainer(
        "psql",
        "-U",
        postgres.username,
        "-d",
        postgres.databaseName,
        "-c",
        "TRUNCATE TABLE asset_tree, asset_variant, asset_label, asset_tag, scheduled_tasks, outbox;",
    )
}

fun createR2dbcDslContext(postgres: PostgreSQLContainer<out PostgreSQLContainer<*>>): DSLContext {
    val options =
        ConnectionFactoryOptions
            .builder()
            .option(DRIVER, "pool")
            .option(PROTOCOL, "postgresql")
            .option(HOST, postgres.host)
            .option(PORT, postgres.getMappedPort(5432))
            .option(USER, postgres.username)
            .option(PASSWORD, postgres.password)
            .option(DATABASE, postgres.databaseName)
            .build()

    val connectionFactory = ConnectionFactories.get(options)
    migrateSchema(connectionFactory)
    return configureR2dbcJOOQ(connectionFactory)
}
