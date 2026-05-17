package io.konifer.config

import com.typesafe.config.ConfigFactory
import io.konifer.client.KoniferClient
import io.konifer.client.KoniferInternalTestApi
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

class KoniferTestScope(
    private val builder: ApplicationTestBuilder,
    coroutineScope: CoroutineScope,
) : CoroutineScope by coroutineScope {
    private var currentClient = createClient()

    val client: HttpClient get() = currentClient

    @OptIn(KoniferInternalTestApi::class)
    val konifer get() = KoniferClient.buildForTesting(client)

    /**
     * Configure the test [io.ktor.client.HttpClient]. JSON content negotiation is already enabled.
     */
    fun configureClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient {
        currentClient.close()
        currentClient = createClient(block)
        return currentClient
    }

    private fun createClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {}) =
        builder.createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        explicitNulls = false
                    },
                )
            }
            block()
        }
}

fun testInMemory(
    configuration: String? = null,
    testBody: suspend KoniferTestScope.() -> Unit,
) {
    testApplication {
        routing {
            get("/test-image") {
                val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
                call.respondBytes(image, ContentType.Application.OctetStream)
            }
        }
        environment {
            val inMemoryConfig =
                ConfigFactory.parseString(
                    """
                    object-store {
                        provider = in-memory
                    }
                    data-store {
                        provider = in-memory
                    }
                    """.trimIndent(),
                )
            config =
                HoconApplicationConfig(ConfigFactory.load())
                    .mergeWith(HoconApplicationConfig(inMemoryConfig))
                    .let { cfg ->
                        configuration?.let {
                            cfg.mergeWith(HoconApplicationConfig(ConfigFactory.parseString(it)))
                        } ?: cfg
                    }
        }
        coroutineScope {
            KoniferTestScope(this@testApplication, this).testBody()
        }
    }
}
