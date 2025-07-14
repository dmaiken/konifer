package io

import asset.assetModule
import image.imageModule
import io.aws.awsModule
import io.database.dbModule
import io.inmemory.inMemoryObjectStoreModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.path.pathModule
import io.r2dbc.spi.ConnectionFactory
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin(
    connectionFactory: ConnectionFactory?,
    inMemoryObjectStoreEnabled: Boolean,
) {
    install(Koin) {
        slf4jLogger()
        modules(
            dbModule(connectionFactory),
            assetModule(connectionFactory),
            if (inMemoryObjectStoreEnabled) inMemoryObjectStoreModule() else awsModule(),
            pathModule(),
            imageModule(),
        )
    }
}
