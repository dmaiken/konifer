package io.asset.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.server.application.Application
import io.ktor.server.engine.ConnectorType
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.dsl.module
import java.net.http.HttpClient as JavaHttpClient

private val logger = KtorSimpleLogger("io.asset.http")

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

fun Application.httpModule(): Module =
    module {
        val port: Int =
            runBlocking(Dispatchers.IO) {
                engine.resolvedConnectors().first { connector ->
                    connector.type == ConnectorType.HTTP
                }.port
            }
        logger.info("Port is: $port")
        single<AssetUrlGenerator> {
            AssetUrlGenerator(port)
        }
    }
