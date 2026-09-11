package io.konifer.infrastructure.asset

import io.konifer.domain.ByteSize
import io.konifer.domain.ports.AssetSourceForbiddenException
import io.konifer.domain.ports.AssetSourceUnavailableException
import io.konifer.domain.ports.ExternalContentReference
import io.konifer.domain.ports.FetchResult
import io.konifer.domain.ports.InvalidAssetSourceException
import io.konifer.domain.ports.RemoteAssetTooLargeException
import io.konifer.infrastructure.objectstore.s3.AwsS3SourceReader
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
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.time.Duration.Companion.seconds

class ExternalSourceContainerFactoryTest {
    @Test
    fun `materializes response before leaving streaming scope`() =
        runTest {
            val content = "image-content".encodeToByteArray()
            val engine = MockEngine { respond(content) }
            val factory = createFactory(engine, maxBytes = content.size.toLong())

            val container = factory.fromSource(url("https://assets.example/image"))
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

            factory.fromSource(url("https://assets.example/start")).use { container ->
                container.getTemporaryFile().readBytes() shouldBe content
            }

            engine.requestHistory.map { it.url.encodedPath } shouldContainExactly
                listOf("/start", "/middle", "/image")
        }

    @Test
    fun `rejects an initial URL outside the allowlist before making a request`() =
        runTest {
            val engine = MockEngine { error("The disallowed URL must not be requested") }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<AssetSourceForbiddenException> {
                    factory.fromSource(url("https://forbidden.example/image"))
                }

            exception.message shouldBe "Not permitted host domain: forbidden.example"
            engine.requestHistory.size shouldBe 0
        }

    @Test
    fun `rejects an initial URL with an unsupported scheme`() =
        runTest {
            val engine = MockEngine { error("The unsupported URL must not be requested") }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<InvalidAssetSourceException> {
                    factory.fromSource(url("ftp://assets.example/image"))
                }

            exception.message shouldBe "Asset source must use HTTP or HTTPS"
            engine.requestHistory.size shouldBe 0
        }

    @Test
    fun `rejects an initial URL without a host`() =
        runTest {
            val engine = MockEngine { error("The invalid URL must not be requested") }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<InvalidAssetSourceException> {
                    factory.fromSource(url("https:/image"))
                }

            exception.message shouldBe "Asset source must include a valid host"
            engine.requestHistory.size shouldBe 0
        }

    @Test
    fun `rejects a redirect to a domain outside the allowlist`() =
        runTest {
            val engine = MockEngine { redirect("https://forbidden.example/image") }
            val factory = createFactory(engine)

            shouldThrow<AssetSourceForbiddenException> {
                factory.fromSource(url("https://assets.example/start"))
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
                    factory.fromSource(url("https://assets.example/start"))
                }

            exception.message shouldBe "Asset source cannot redirect from HTTPS to HTTP"
            engine.requestHistory.size shouldBe 1
        }

    @Test
    fun `rejects a redirect without a location`() =
        runTest {
            val engine = MockEngine { respond(ByteArray(0), status = HttpStatusCode.Found) }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<InvalidAssetSourceException> {
                    factory.fromSource(url("https://assets.example/start"))
                }

            exception.message shouldBe "Asset source returned redirect 302 without a Location header"
        }

    @Test
    fun `rejects a redirect with an invalid URL`() =
        runTest {
            val engine = MockEngine { redirect("https://[invalid") }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<InvalidAssetSourceException> {
                    factory.fromSource(url("https://assets.example/start"))
                }

            exception.message shouldBe "Asset source returned an invalid redirect URL"
        }

    @Test
    fun `rejects a redirect with an unsupported status`() =
        runTest {
            val engine = MockEngine { respond(ByteArray(0), status = HttpStatusCode.NotModified) }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<InvalidAssetSourceException> {
                    factory.fromSource(url("https://assets.example/start"))
                }

            exception.message shouldBe "Asset source returned unsupported redirect 304"
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
                    factory.fromSource(url("https://assets.example/one"))
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
                    factory.fromSource(url("https://assets.example/0"))
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
                factory.fromSource(url("https://assets.example/image"))
            }
        }

    @Test
    fun `rejects an oversized content length before reading the body`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = ByteArray(0),
                        headers = headersOf(HttpHeaders.ContentLength, "6"),
                    )
                }
            val factory = createFactory(engine, maxBytes = 5)

            shouldThrow<RemoteAssetTooLargeException> {
                factory.fromSource(url("https://assets.example/image"))
            }
        }

    @Test
    fun `rejects an invalid content length`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = ByteArray(0),
                        headers = headersOf(HttpHeaders.ContentLength, "invalid"),
                    )
                }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<AssetSourceUnavailableException> {
                    factory.fromSource(url("https://assets.example/image"))
                }

            exception.message shouldBe "Asset source returned an invalid Content-Length header"
        }

    @Test
    fun `maps a client error to an invalid asset source`() =
        runTest {
            val engine = MockEngine { respond(ByteArray(0), status = HttpStatusCode.NotFound) }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<InvalidAssetSourceException> {
                    factory.fromSource(url("https://assets.example/missing"))
                }

            exception.message shouldBe "Asset source returned 404"
        }

    @Test
    fun `maps a server error to an unavailable asset source`() =
        runTest {
            val engine = MockEngine { respond(ByteArray(0), status = HttpStatusCode.ServiceUnavailable) }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<AssetSourceUnavailableException> {
                    factory.fromSource(url("https://assets.example/image"))
                }

            exception.message shouldBe "Asset source returned 503"
        }

    @Test
    fun `maps an IO failure to an unavailable asset source`() =
        runTest {
            val cause = IOException("Connection closed")
            val engine = MockEngine { throw cause }
            val factory = createFactory(engine)

            val exception =
                shouldThrow<AssetSourceUnavailableException> {
                    factory.fromSource(url("https://assets.example/image"))
                }

            exception.message shouldBe "Failed to retrieve asset source"
            exception.cause shouldBe cause
        }

    @Test
    fun `materializes an S3 object while the reader is producing it`() =
        runTest {
            val content = ByteArray(256 * 1024) { index -> index.toByte() }
            val sourceReader = mockk<AwsS3SourceReader>()
            coEvery {
                sourceReader.fetch(
                    bucket = "source-assets",
                    key = "nested/image.png",
                    channel = any(),
                )
            } coAnswers {
                thirdArg<ByteWriteChannel>().apply {
                    writeFully(content)
                    flushAndClose()
                }
                FetchResult.found(content.size.toLong())
            }
            val factory = createFactory(awsS3SourceReader = sourceReader, maxBytes = content.size.toLong())

            val container =
                withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(5.seconds) {
                        factory.fromSource(s3Object(bucket = "source-assets", key = "nested/image.png"))
                    }
                }

            container.use {
                it.isDumpedToFile shouldBe true
                it.getTemporaryFile().readBytes() shouldBe content
            }
            coVerify(exactly = 1) {
                sourceReader.fetch(
                    bucket = "source-assets",
                    key = "nested/image.png",
                    channel = any(),
                )
            }
        }

    @Test
    fun `maps a missing S3 object to an invalid asset source`() =
        runTest {
            val sourceReader = mockk<AwsS3SourceReader>()
            coEvery { sourceReader.fetch(any(), any(), any()) } coAnswers {
                thirdArg<ByteWriteChannel>().flushAndClose()
                FetchResult.NOT_FOUND
            }
            val factory = createFactory(awsS3SourceReader = sourceReader)

            val exception =
                shouldThrow<InvalidAssetSourceException> {
                    factory.fromSource(s3Object(bucket = "source-assets", key = "missing.png"))
                }

            exception.message shouldBe "S3 object not found"
        }

    @Test
    fun `enforces the size limit while streaming an S3 object`() =
        runTest {
            val sourceReader = mockk<AwsS3SourceReader>()
            coEvery { sourceReader.fetch(any(), any(), any()) } coAnswers {
                thirdArg<ByteWriteChannel>().apply {
                    writeFully(ByteArray(6))
                    flushAndClose()
                }
                FetchResult.found(6)
            }
            val factory = createFactory(awsS3SourceReader = sourceReader, maxBytes = 5)

            shouldThrow<RemoteAssetTooLargeException> {
                factory.fromSource(s3Object(bucket = "source-assets", key = "large.png"))
            }
        }

    @Test
    fun `rejects an S3 object whose reported size exceeds the limit`() =
        runTest {
            val sourceReader = mockk<AwsS3SourceReader>()
            coEvery { sourceReader.fetch(any(), any(), any()) } coAnswers {
                thirdArg<ByteWriteChannel>().apply {
                    writeFully(byteArrayOf(1))
                    flushAndClose()
                }
                FetchResult.found(6)
            }
            val factory = createFactory(awsS3SourceReader = sourceReader, maxBytes = 5)

            shouldThrow<RemoteAssetTooLargeException> {
                factory.fromSource(s3Object(bucket = "source-assets", key = "large.png"))
            }
        }

    @Test
    fun `propagates an S3 source reader failure`() =
        runTest {
            val cause = AssetSourceUnavailableException("S3 is unavailable")
            val sourceReader = mockk<AwsS3SourceReader>()
            coEvery { sourceReader.fetch(any(), any(), any()) } throws cause
            val factory = createFactory(awsS3SourceReader = sourceReader)

            val exception =
                shouldThrow<AssetSourceUnavailableException> {
                    factory.fromSource(s3Object(bucket = "source-assets", key = "image.png"))
                }

            exception shouldBe cause
        }

    private fun createFactory(
        engine: MockEngine = MockEngine { error("HTTP client is not used by S3 tests") },
        maxBytes: Long = 1024,
        awsS3SourceReader: AwsS3SourceReader =
            AwsS3SourceReader(lazy { error("S3 source reader is not used by HTTP tests") }),
    ): ExternalSourceContainerFactory {
        val client =
            HttpClient(engine) {
                followRedirects = false
            }
        return ExternalSourceContainerFactory(
            allowedDomains = setOf("assets.example"),
            maxBytes = ByteSize.parse(maxBytes.toString()),
            httpClient = client,
            awsS3SourceReader = awsS3SourceReader,
        )
    }

    private fun url(value: String) = ExternalContentReference.Url(URI.create(value))

    private fun s3Object(
        bucket: String,
        key: String,
    ) = ExternalContentReference.S3Object(bucket = bucket, key = key)

    private fun MockRequestHandleScope.redirect(location: String) =
        respond(
            content = ByteArray(0),
            status = HttpStatusCode.Found,
            headers = headersOf(HttpHeaders.Location, location),
        )
}
