package io.direkt.infrastructure

import io.direkt.asset.assetModule
import io.direkt.infrastructure.http.httpClientModule
import io.direkt.infrastructure.http.httpModule
import io.direkt.infrastructure.database.postgres.dbModule
import io.direkt.infrastructure.variant.variantModule
import io.direkt.infrastructure.objectstore.inmemory.inMemoryObjectStoreModule
import io.direkt.infrastructure.path.pathModule
import io.direkt.infrastructure.objectstore.s3.s3Module
import io.direkt.infrastructure.vips.vipsModule
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
            vipsModule(),
        )
    }
}
