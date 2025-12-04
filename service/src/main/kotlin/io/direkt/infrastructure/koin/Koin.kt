package io.direkt.infrastructure.koin

import io.direkt.asset.assetModule
import io.direkt.asset.http.httpClientModule
import io.direkt.asset.http.httpModule
import io.direkt.database.dbModule
import io.direkt.infrastructure.variant.variantModule
import io.direkt.inmemory.inMemoryObjectStoreModule
import io.direkt.path.pathModule
import io.direkt.s3.s3Module
import io.image.imageModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
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
            variantModule(),
            if (inMemoryObjectStoreEnabled) inMemoryObjectStoreModule() else s3Module(),
            pathModule(),
            imageModule(),
        )
    }
}
