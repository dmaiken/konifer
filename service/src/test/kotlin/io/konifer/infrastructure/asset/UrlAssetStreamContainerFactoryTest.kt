package io.konifer.infrastructure.asset

import io.konifer.domain.ByteSize
import io.konifer.domain.ports.AssetSourceForbiddenException
import io.konifer.domain.ports.InvalidAssetSourceException
import io.konifer.domain.ports.RemoteAssetTooLargeException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.io.path.exists
import kotlin.io.path.readBytes

class UrlAssetStreamContainerFactoryTest {
    @Test
    fun `materializes response before leaving streaming scope`() =
        runTest {
            val content = "image-content".encodeToByteArray()
            val engine = MockEngine { respond(content) }
            val factory = createFactory(engine, maxBytes = content.size.toLong())

            val container = factory.fromUrlSource("https://assets.example/image")
            val path = container.getTemporaryFile()

            container.isDumpedToFile shouldBe true
            path.readBytes() shouldBe content

            container.close()
            path.exists() shouldBe false
        }

    @Test
    fun `follows relative redirect chains and materializes final response`() =
        runTest {
            val content = "image-content".encodeToByteArray()
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/start" -> redirect("/middle")
                        "/middle" -> redirect("/image")
                        "/image" -> respond(content)
                        else -> error("Unexpected request to ${request.url}")
                    }
                }
            val factory = createFactory(engine)

            factory.fromUrlSource("https://assets.example/start").use { container ->
                container.getTemporaryFile().readBytes() shouldBe content
            }

            engine.requestHistory.map { it.url.encodedPath } shouldContainExactly
                listOf("/start", "/middle", "/image")
        }

    @Test
    fun `rejects a redirect to a domain outside the allowlist`() =
        runTest {
            val engine = MockEngine { redirect("https://forbidden.example/image") }
            val factory = createFactory(engine)

            shouldThrow<AssetSourceForbiddenException> {
                factory.fromUrlSource("https://assets.example/start")
            }

            engine.requestHistory.size shouldBe 1
        }

    @Test
    fun `rejects an HTTPS to HTTP redirect`() =
        runTest {
            val engine = MockEngine { redirect("http://assets.example/image") }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<InvalidAssetSourceException> {
                    factory.fromUrlSource("https://assets.example/start")
                }

            exception.message shouldBe "Asset source cannot redirect from HTTPS to HTTP"
            engine.requestHistory.size shouldBe 1
        }

    @Test
    fun `rejects redirect loops`() =
        runTest {
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/one" -> redirect("/two")
                        "/two" -> redirect("/one")
                        else -> error("Unexpected request to ${request.url}")
                    }
                }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<InvalidAssetSourceException> {
                    factory.fromUrlSource("https://assets.example/one")
                }

            exception.message shouldBe "Asset source redirect loop detected"
            engine.requestHistory.size shouldBe 2
        }

    @Test
    fun `stops after five redirects`() =
        runTest {
            val engine =
                MockEngine { request ->
                    val next =
                        request.url.encodedPath
                            .removePrefix("/")
                            .toInt() + 1
                    redirect("/$next")
                }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<InvalidAssetSourceException> {
                    factory.fromUrlSource("https://assets.example/0")
                }

            exception.message shouldBe "Asset source exceeded the maximum of 5 redirects"
            engine.requestHistory.size shouldBe 6
        }

    @Test
    fun `enforces actual body size without content length`() =
        runTest {
            val engine = MockEngine { respond(ByteArray(6)) }
            val factory = createFactory(engine, maxBytes = 5)

            shouldThrow<RemoteAssetTooLargeException> {
                factory.fromUrlSource("https://assets.example/image")
            }
        }

    private fun createFactory(
        engine: MockEngine,
        maxBytes: Long = 1024,
    ): UrlAssetStreamContainerFactory {
        val client =
            HttpClient(engine) {
                followRedirects = false
            }
        return UrlAssetStreamContainerFactory(
            allowedDomains = setOf("assets.example"),
            maxBytes = ByteSize.parse(maxBytes.toString()),
            httpClient = client,
        )
    }

    private fun MockRequestHandleScope.redirect(location: String) =
        respond(
            content = ByteArray(0),
            status = HttpStatusCode.Found,
            headers = headersOf(HttpHeaders.Location, location),
        )
}
