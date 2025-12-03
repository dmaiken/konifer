package io.direkt

import io.database.dbModule
import io.direkt.asset.assetModule
import io.direkt.asset.http.httpClientModule
import io.direkt.asset.http.httpModule
import io.direkt.s3.s3Module
import io.image.imageModule
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
            httpClientModule(),
            httpModule(),
            dbModule(connectionFactory),
            assetModule(connectionFactory),
            if (inMemoryObjectStoreEnabled) inMemoryObjectStoreModule() else s3Module(),
            pathModule(),
            imageModule(),
        )
    }
}
