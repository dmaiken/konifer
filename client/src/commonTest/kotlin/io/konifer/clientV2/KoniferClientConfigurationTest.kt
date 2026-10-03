package io.konifer.clientV2

import io.konifer.client.asset.info.createInfoResponse
import io.konifer.client.harness.assertSignatureParameter
import io.konifer.clientV2.assets.fetch.ContentDelivery
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class KoniferClientConfigurationTest :
    FunSpec({
        test("public engine injection retains base URL, JSON configuration, signing, and default timeouts") {
            val asset = createInfoResponse()
            val engine =
                MockEngine { request ->
                    request.url.host shouldBe "konifer.example"
                    request.url.port shouldBe 8443
                    request.getCapabilityOrNull(HttpTimeoutCapability) shouldBe null
                    if (request.method == HttpMethod.Post) {
                        request.url.encodedPath shouldBe "/assets/profile"
                        val body = request.body.shouldBeInstanceOf<TextContent>()
                        val json = Json.parseToJsonElement(body.text).shouldBeInstanceOf<JsonObject>()
                        json.containsKey("alt") shouldBe false
                    } else {
                        request.url.encodedPath shouldBe "/assets/profile/-/new/info"
                        assertSignatureParameter(request.url.parameters, true)
                    }
                    val json = Json.encodeToString(asset).dropLast(1) + ",\"futureField\":true}"
                    respond(json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val client = KoniferClientV2.build("https://konifer.example:8443", hmacKey = "secret", engine = engine)
            try {
                client.assets("profile").fetchInfo() shouldBe KoniferV2Result.Success(asset)
                client
                    .assets("profile")
                    .newAsset()
                    .fromUrl("https://images.example/avatar.png")
                    .store() shouldBe
                    KoniferV2Result.Success(asset)
            } finally {
                client.close()
                engine.close()
            }
        }

        test("timeouts are applied to both service requests and redirected downloads") {
            val bytes = byteArrayOf(1, 2, 3)
            val engine =
                MockEngine { request ->
                    val timeouts = request.getCapabilityOrNull(HttpTimeoutCapability)!!
                    timeouts.requestTimeoutMillis shouldBe 30_000L
                    timeouts.connectTimeoutMillis shouldBe 5_000L
                    timeouts.socketTimeoutMillis shouldBe 10_000L
                    if (request.url.host == "delivery.example") {
                        respond(bytes)
                    } else {
                        respond(
                            "",
                            status = HttpStatusCode.TemporaryRedirect,
                            headers = headersOf(HttpHeaders.Location, "https://delivery.example/avatar.png"),
                        )
                    }
                }
            val client =
                KoniferClientV2.build(
                    "https://konifer.example",
                    httpConfiguration =
                        KoniferHttpConfiguration(
                            requestTimeout = 30.seconds,
                            connectTimeout = 5.seconds,
                            socketTimeout = 10.seconds,
                        ),
                    engine = engine,
                )
            try {
                client.assets("profile").originalVariant().fetchContentBytes(ContentDelivery.FOLLOW_REDIRECT) shouldBe
                    KoniferV2Result.Success(bytes)
                engine.requestHistory.size shouldBe 2
            } finally {
                client.close()
                engine.close()
            }
        }

        test("request deadlines return transport failures without swallowing caller cancellation") {
            val engine = MockEngine { awaitCancellation() }
            val client =
                KoniferClientV2.build(
                    "https://konifer.example",
                    httpConfiguration = KoniferHttpConfiguration(requestTimeout = 100.milliseconds),
                    engine = engine,
                )
            try {
                val result = withTimeout(5.seconds) { client.assets("profile").originalVariant().fetchContentBytes() }
                result
                    .shouldBeInstanceOf<KoniferV2Result.Failure.Transport>()
                    .cause
                    .shouldBeInstanceOf<HttpRequestTimeoutException>()
            } finally {
                client.close()
                engine.close()
            }

            val cancellationEngine = MockEngine { awaitCancellation() }
            val cancellationClient = KoniferClientV2.build("https://konifer.example", engine = cancellationEngine)
            try {
                shouldThrow<TimeoutCancellationException> {
                    withTimeout(100.milliseconds) { cancellationClient.assets("profile").originalVariant().fetchContentBytes() }
                }
            } finally {
                cancellationClient.close()
                cancellationEngine.close()
            }
        }

        test("infinite timeout values explicitly disable deadlines") {
            val engine =
                MockEngine { request ->
                    val timeouts = request.getCapabilityOrNull(HttpTimeoutCapability)!!
                    timeouts.requestTimeoutMillis shouldBe Long.MAX_VALUE
                    timeouts.connectTimeoutMillis shouldBe Long.MAX_VALUE
                    timeouts.socketTimeoutMillis shouldBe Long.MAX_VALUE
                    respond(byteArrayOf(1))
                }
            val client =
                KoniferClientV2.build(
                    "https://konifer.example",
                    httpConfiguration = KoniferHttpConfiguration(Duration.INFINITE, Duration.INFINITE, Duration.INFINITE),
                    engine = engine,
                )
            try {
                client.assets("profile").originalVariant().fetchContentBytes() shouldBe KoniferV2Result.Success(byteArrayOf(1))
            } finally {
                client.close()
                engine.close()
            }
        }

        test("closing clients and their redirect clients leaves a shared injected engine usable") {
            val bytes = byteArrayOf(4, 5)
            val engine = MockEngine { respond(bytes) }
            val first = KoniferClientV2.build("https://konifer.example", engine = engine)
            val second = KoniferClientV2.build("https://konifer.example", engine = engine)
            try {
                first.assets("profile").originalVariant().fetchContentBytes(ContentDelivery.FOLLOW_REDIRECT) shouldBe
                    KoniferV2Result.Success(bytes)
                first.close()
                first.close()
                engine.coroutineContext[Job]!!.isActive shouldBe true
                second.assets("profile").originalVariant().fetchContentBytes() shouldBe KoniferV2Result.Success(bytes)
                second.close()
                engine.coroutineContext[Job]!!.isActive shouldBe true
            } finally {
                first.close()
                second.close()
                engine.close()
            }
        }

        test("closing a client also closes its owned engine after closing the redirect client") {
            val httpClient = HttpClient(MockEngine) { engine { addHandler { respond(byteArrayOf(1)) } } }
            val client = KoniferClientV2(httpClient)
            try {
                client.assets("profile").originalVariant().fetchContentBytes(ContentDelivery.FOLLOW_REDIRECT) shouldBe
                    KoniferV2Result.Success(byteArrayOf(1))
            } finally {
                client.close()
            }
            withTimeout(5.seconds) { httpClient.engine.coroutineContext[Job]!!.join() }
            httpClient.engine.coroutineContext[Job]!!.isCompleted shouldBe true
        }

        test("configuration rejects nonpositive and submillisecond timeouts") {
            listOf(Duration.ZERO, (-1).seconds, 1.nanoseconds, -Duration.INFINITE).forEach { invalid ->
                shouldThrow<IllegalArgumentException> { KoniferHttpConfiguration(requestTimeout = invalid) }
                shouldThrow<IllegalArgumentException> { KoniferHttpConfiguration(connectTimeout = invalid) }
                shouldThrow<IllegalArgumentException> { KoniferHttpConfiguration(socketTimeout = invalid) }
            }
        }

        test("Java builder produces immutable configurations and supports defaults and infinity") {
            val builder =
                KoniferHttpConfiguration
                    .Builder()
                    .requestTimeoutMillis(30_000)
                    .connectTimeoutMillis(5_000)
                    .socketTimeoutMillis(Long.MAX_VALUE)
            val first = builder.build()
            builder.requestTimeoutMillis(null).connectTimeoutMillis(10_000).socketTimeoutMillis(null)
            val second = builder.build()
            first.requestTimeout shouldBe 30.seconds
            first.connectTimeout shouldBe 5.seconds
            first.socketTimeout shouldBe Duration.INFINITE
            second.requestTimeout shouldBe null
            second.connectTimeout shouldBe 10.seconds
            second.socketTimeout shouldBe null
            shouldThrow<IllegalArgumentException> { builder.requestTimeoutMillis(0).build() }
        }
    })
