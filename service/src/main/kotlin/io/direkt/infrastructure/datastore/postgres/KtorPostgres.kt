package io.direkt.infrastructure.datastore.postgres

import io.ktor.server.application.Application
import io.ktor.server.application.log
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

fun Application.connectToPostgres(properties: PostgresProperties): ConnectionFactory {
    log.info("(R2DBC) Connecting to postgres database")
    val options =
        builder()
            .option(R2DBC_DATABASE, properties.database)
            .option(R2DBC_DRIVER, "pool")
            .option(R2DBC_PROTOCOL, "postgresql")
            .option(R2DBC_USER, properties.user)
            .option(R2DBC_HOST, properties.host)
            .option(R2DBC_PORT, properties.port)
    properties.password?.let {
        options.option(R2DBC_PASSWORD, it)
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
