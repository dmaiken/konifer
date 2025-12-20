package io.direkt.infrastructure.datastore.postgres

import io.direkt.infrastructure.properties.ConfigurationProperties.DATASTORE
import io.direkt.infrastructure.properties.ConfigurationProperties.DatabaseConfigurationProperties.POSTGRES
import io.direkt.infrastructure.properties.ConfigurationProperties.DatabaseConfigurationProperties.PostgresConfigurationProperties.DATABASE
import io.direkt.infrastructure.properties.ConfigurationProperties.DatabaseConfigurationProperties.PostgresConfigurationProperties.HOST
import io.direkt.infrastructure.properties.ConfigurationProperties.DatabaseConfigurationProperties.PostgresConfigurationProperties.PASSWORD
import io.direkt.infrastructure.properties.ConfigurationProperties.DatabaseConfigurationProperties.PostgresConfigurationProperties.PORT
import io.direkt.infrastructure.properties.ConfigurationProperties.DatabaseConfigurationProperties.PostgresConfigurationProperties.USER
import io.direkt.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.config.tryGetString
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions.builder
import name.nkonev.r2dbc.migrate.core.R2dbcMigrate
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties
import name.nkonev.r2dbc.migrate.reader.ReflectionsClasspathResourceReader
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.jooq.tools.LoggerListener
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE as R2DBC_DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER as R2DBC_DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.HOST as R2DBC_HOST
import io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD as R2DBC_PASSWORD
import io.r2dbc.spi.ConnectionFactoryOptions.PORT as R2DBC_PORT
import io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL as R2DBC_PROTOCOL
import io.r2dbc.spi.ConnectionFactoryOptions.USER as R2DBC_USER

fun Application.connectToPostgres(): ConnectionFactory {
    log.info("Connecting to postgres database")
    val postgresProperties =
        environment.config
            .tryGetConfig(DATASTORE)
            ?.tryGetConfig(POSTGRES)
    val password =
        postgresProperties
            ?.tryGetString(PASSWORD)
    val host =
        postgresProperties
            ?.tryGetString(HOST)
            ?: "localhost"
    val database =
        postgresProperties
            ?.tryGetString(DATABASE)
            ?: "direkt"
    val port =
        postgresProperties
            ?.tryGetString(PORT)
            ?.toInt()
            ?: 5432
    val user =
        postgresProperties
            ?.tryGetString(USER)
            ?: throw IllegalStateException("$DATASTORE.$POSTGRES.$USER must be supplied if using Postgres datastore")
    val options =
        builder()
            .option(R2DBC_DATABASE, database)
            .option(R2DBC_DRIVER, "pool")
            .option(R2DBC_PROTOCOL, "postgresql")
            .option(R2DBC_USER, user)
            .option(R2DBC_HOST, host)
            .option(R2DBC_PORT, port)
    password?.let {
        options.option(R2DBC_PASSWORD, password)
    }

    return ConnectionFactories.get(options.build())
}

fun migrateSchema(connectionFactory: ConnectionFactory) {
    val migrateProperties =
        R2dbcMigrateProperties().apply {
            setResourcesPath("db/migration")
        }

    R2dbcMigrate.migrate(connectionFactory, migrateProperties, ReflectionsClasspathResourceReader(), null, null).block()
}

fun configureJOOQ(connectionFactory: ConnectionFactory): DSLContext {
    val config =
        DefaultConfiguration().apply {
            setSQLDialect(SQLDialect.POSTGRES)
            setConnectionFactory(connectionFactory)
            setExecuteListener(LoggerListener())
            settings()
                .withExecuteLogging(true)
                .withRenderFormatted(true)
        }

    return DSL.using(config)
}
