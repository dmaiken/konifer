package io.konifer.clientV2

import io.konifer.client.asset.info.createInfoResponse
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.clientV2.assets.fetch.ContentDelivery
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class KoniferBlockingClientConfigurationTest :
    FunSpec({
        test("public blocking factory shares configuration, serialization, signing, and engine ownership") {
            val asset = createInfoResponse()
            val bytes = byteArrayOf(1, 2, 3)
            val engine =
                MockEngine { request ->
                    request.url.host shouldBe "konifer.example"
                    val timeouts = request.getCapabilityOrNull(HttpTimeoutCapability)!!
                    timeouts.requestTimeoutMillis shouldBe 30_000L
                    timeouts.connectTimeoutMillis shouldBe 5_000L
                    timeouts.socketTimeoutMillis shouldBe 10_000L
                    assertSignatureParameter(request.url.parameters, true)
                    if (request.url.encodedPath.endsWith("/info")) {
                        val json = Json.encodeToString(asset).dropLast(1) + ",\"futureField\":true}"
                        respond(json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        respond(bytes)
                    }
                }
            engine.use { engine ->
                KoniferBlockingClientV2
                    .build(
                        "https://konifer.example",
                        hmacKey = "secret",
                        httpConfiguration = KoniferHttpConfiguration(30.seconds, 5.seconds, 10.seconds),
                        engine = engine,
                    ).use { client ->
                        client.assets("profile").fetchInfo() shouldBe KoniferV2Result.Success(asset)
                        client.assets("profile").originalVariant().fetchContentBytes(ContentDelivery.FOLLOW_REDIRECT) shouldBe
                            KoniferV2Result.Success(bytes)
                    }
                engine.coroutineContext[Job]!!.isActive shouldBe true
            }
        }
    })
