package io

import io.asset.assetModule
import io.database.dbModule
import io.image.imageModule
import io.inmemory.inMemoryObjectStoreModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.path.pathModule
import io.r2dbc.spi.ConnectionFactory
import io.s3.s3Module
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.net.http.HttpClient as JavaHttpClient

fun Application.configureKoin(
    connectionFactory: ConnectionFactory?,
    inMemoryObjectStoreEnabled: Boolean,
) {
    install(Koin) {
        slf4jLogger()
        modules(
            httpClientModule(),
            dbModule(connectionFactory),
            assetModule(connectionFactory),
            if (inMemoryObjectStoreEnabled) inMemoryObjectStoreModule() else s3Module(),
            pathModule(),
            imageModule(),
        )
    }
}

fun httpClientModule(): Module =
    module {
        single<HttpClient> {
            HttpClient(Java) {
                engine {
                    dispatcher = Dispatchers.IO
                    pipelining = true
                    protocolVersion = JavaHttpClient.Version.HTTP_2
                }
            }
        }
    }
