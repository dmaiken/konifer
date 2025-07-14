package io.database

import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.HOST
import io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD
import io.r2dbc.spi.ConnectionFactoryOptions.PORT
import io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL
import io.r2dbc.spi.ConnectionFactoryOptions.USER
import io.r2dbc.spi.ConnectionFactoryOptions.builder
import name.nkonev.r2dbc.migrate.core.R2dbcMigrate
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties
import name.nkonev.r2dbc.migrate.reader.ReflectionsClasspathResourceReader
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.jooq.tools.LoggerListener

fun Application.connectToPostgres(): ConnectionFactory {
    log.info("Connecting to postgres database")
    val user = environment.config.property("postgres.user").getString()
    val password = environment.config.property("postgres.password").getString()
    val host = environment.config.property("postgres.host").getString()
    val port = environment.config.property("postgres.port").getString().toInt()
    val options =
        builder()
            .option(DATABASE, "tessa")
            .option(DRIVER, "pool")
            .option(PROTOCOL, "postgresql")
            .option(USER, user)
            .option(PASSWORD, password)
            .option(HOST, host)
            .option(PORT, port)
            .build()

    return ConnectionFactories.get(options)
}

fun migrateSchema(connectionFactory: ConnectionFactory) {
    val migrateProperties =
        R2dbcMigrateProperties().apply {
            setResourcesPath("db/migration")
        }

    R2dbcMigrate.migrate(connectionFactory, migrateProperties, ReflectionsClasspathResourceReader(), null, null).block()
}

fun configureJOOQ(connectionFactory: ConnectionFactory): DSLContext {
    val settings =
        Settings()
            .withExecuteLogging(true) // enables jOOQ logging
            .withRenderFormatted(true) // optional: pretty SQL

    val config =
        DefaultConfiguration().apply {
            setSQLDialect(SQLDialect.POSTGRES)
            setConnectionFactory(connectionFactory)
            setExecuteListener(LoggerListener())
            settings()
                .withExecuteLogging(true) // enables jOOQ logging
                .withRenderFormatted(true) // optional: pretty SQL
        }

    return DSL.using(config)
}
