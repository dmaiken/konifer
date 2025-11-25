package io.asset.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import java.net.http.HttpClient as JavaHttpClient

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
        single<AssetUrlGenerator> {
            AssetUrlGenerator(8080)
        }
    }
