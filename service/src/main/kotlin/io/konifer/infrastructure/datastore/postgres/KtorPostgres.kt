package io.konifer.infrastructure.datastore.postgres

import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions.builder
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE as R2DBC_DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER as R2DBC_DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.HOST as R2DBC_HOST
import io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD as R2DBC_PASSWORD
import io.r2dbc.spi.ConnectionFactoryOptions.PORT as R2DBC_PORT
import io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL as R2DBC_PROTOCOL
import io.r2dbc.spi.ConnectionFactoryOptions.USER as R2DBC_USER

fun Application.connectToPostgres(properties: PostgresProperties): ConnectionFactory {
    log.info("(R2DBC) Connecting to postgres on ${properties.host}:${properties.port} using database: ${properties.database}")
    val options =
        builder()
            .option(R2DBC_DATABASE, properties.database)
            .option(R2DBC_DRIVER, "pool")
            .option(R2DBC_PROTOCOL, "postgresql")
            .option(R2DBC_USER, properties.user)
            .option(R2DBC_HOST, properties.host)
            .option(R2DBC_PORT, properties.port)
            .option(R2DBC_PASSWORD, properties.password)

    return ConnectionFactories.get(options.build())
}
