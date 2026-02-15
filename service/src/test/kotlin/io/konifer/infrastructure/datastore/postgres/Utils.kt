package io.konifer.infrastructure.datastore.postgres

import io.konifer.infrastructure.datastore.configureR2dbcJOOQ
import io.konifer.infrastructure.datastore.migrateSchema
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
        "TRUNCATE TABLE asset_tree, path_entry_counter, asset_variant, asset_label, asset_tag, scheduled_tasks, outbox;",
    )
}

fun createR2dbcDslContext(postgres: PostgreSQLContainer<out PostgreSQLContainer<*>>): DSLContext {
    val connectionFactory =
        connectToPostgres(
            PostgresProperties(
                database = postgres.databaseName,
                user = postgres.username,
                host = postgres.host,
                port = postgres.getMappedPort(5432),
                password = postgres.password,
                sslMode = "prefer",
            ),
        )
    installLtree(postgres)
    migrateSchema(connectionFactory)
    return configureR2dbcJOOQ(connectionFactory)
}

fun installLtree(postgres: PostgreSQLContainer<out PostgreSQLContainer<*>>) {
    postgres.execInContainer(
        "psql",
        "-U",
        postgres.username,
        "-d",
        postgres.databaseName,
        "-c",
        "CREATE EXTENSION IF NOT EXISTS ltree;",
    )
}
