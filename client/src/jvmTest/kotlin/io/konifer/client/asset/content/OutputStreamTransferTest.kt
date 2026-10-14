package io.konifer.client.asset.content

import io.konifer.client.KoniferBlockingClient
import io.konifer.client.KoniferResult
import io.konifer.client.assets.fetch.AssetContentMetadata
import io.konifer.client.assets.fetch.ContentDelivery
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException

class OutputStreamTransferTest :
    FunSpec({
        withData(nameFn = { "caller-owned streams remain open and unflushed with $it" }, ContentDelivery.entries) { delivery ->
            val bytes = ByteArray(100_000) { (it % 256).toByte() }
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/redirect")) {
                        respond(
                            "",
                            status = HttpStatusCode.TemporaryRedirect,
                            headers = headersOf(HttpHeaders.Location, "https://delivery.example/avatar.png"),
                        )
                    } else {
                        respond(bytes)
                    }
                }
            val output = TrackingOutputStream()
            engine.use { engine ->
                KoniferBlockingClient.build("https://konifer.example", engine = engine).use { client ->
                    val variant = client.assets("profile").originalVariant()
                    variant.fetchAndWriteContentTo(output, delivery) shouldBe KoniferResult.Success(AssetContentMetadata())
                    variant.fetchAndWriteContentTo(output, delivery) shouldBe KoniferResult.Success(AssetContentMetadata())
                    output.contents() shouldBe bytes + bytes
                    output.flushes shouldBe 0
                    output.closed shouldBe false
                    output.write(42)
                    output.contents() shouldBe bytes + bytes + byteArrayOf(42)
                }
                output.closed shouldBe false
            }
        }

        test("buffered output is flushed and closed only by the caller") {
            val bytes = byteArrayOf(1, 2, 3)
            val engine = MockEngine { respond(bytes) }
            val sink = TrackingOutputStream()
            val output = BufferedOutputStream(sink)
            try {
                KoniferBlockingClient.build("https://konifer.example", engine = engine).use { client ->
                    client.assets("profile").originalVariant().fetchAndWriteContentTo(output) shouldBe
                        KoniferResult.Success(AssetContentMetadata())
                    sink.contents() shouldBe byteArrayOf()
                    sink.flushes shouldBe 0
                    sink.closed shouldBe false
                    output.flush()
                    sink.contents() shouldBe bytes
                    sink.flushes shouldBe 1
                }
                sink.closed shouldBe false
            } finally {
                output.close()
                engine.close()
            }
            sink.closed shouldBe true
        }

        test("blocking streaming preserves parsed metadata after writing the bytes") {
            val bytes = byteArrayOf(1, 2, 3)
            val engine = MockEngine { respond(bytes, headers = headersOf("K-Alt", "an image")) }
            val output = TrackingOutputStream()
            engine.use { engine ->
                KoniferBlockingClient.build("https://konifer.example", engine = engine).use { client ->
                    client.assets("profile").originalVariant().fetchAndWriteContentTo(output) shouldBe
                        KoniferResult.Success(AssetContentMetadata(alt = "an image"))
                }
                output.contents() shouldBe bytes
                output.closed shouldBe false
                output.flushes shouldBe 0
            }
        }

        test("invalid response metadata returns a failure without writing or closing caller output") {
            val engine = MockEngine { respond(byteArrayOf(1, 2, 3), headers = headersOf("K-Attr-Width", "invalid")) }
            val output = TrackingOutputStream()
            engine.use { engine ->
                KoniferBlockingClient.build("https://konifer.example", engine = engine).use { client ->
                    client
                        .assets("profile")
                        .originalVariant()
                        .fetchAndWriteContentTo(output)
                        .shouldBeInstanceOf<KoniferResult.Failure.InvalidResponse>()
                }
                output.contents() shouldBe byteArrayOf()
                output.closed shouldBe false
                output.flushes shouldBe 0
            }
        }

        test("an HTTP failure does not write, flush, or close the output and leaves it reusable") {
            val engine =
                MockEngine {
                    respond("unavailable", status = HttpStatusCode.ServiceUnavailable)
                }
            val output = TrackingOutputStream()
            engine.use { engine ->
                KoniferBlockingClient.build("https://konifer.example", engine = engine).use { client ->
                    client.assets("profile").originalVariant().fetchAndWriteContentTo(output) shouldBe
                        KoniferResult.Failure.Http(503, null)
                }
                output.contents() shouldBe byteArrayOf()
                output.flushes shouldBe 0
                output.closed shouldBe false
                output.write(42)
                output.contents() shouldBe byteArrayOf(42)
            }
        }

        test("a transport failure does not flush or close the caller-owned output") {
            val failure = IOException("offline")
            val engine = MockEngine { throw failure }
            val output = TrackingOutputStream()
            engine.use { engine ->
                KoniferBlockingClient.build("https://konifer.example", engine = engine).use { client ->
                    client.assets("profile").originalVariant().fetchAndWriteContentTo(output) shouldBe
                        KoniferResult.Failure.Transport(failure)
                }
                output.contents() shouldBe byteArrayOf()
                output.flushes shouldBe 0
                output.closed shouldBe false
            }
        }

        withData(
            nameFn = { "partial output-write failures return Transport without closing the stream for $it bytes" },
            4,
            100_000,
        ) { size ->
            val bytes = ByteArray(size) { (it % 256).toByte() }
            val failure = IOException("disk full")
            val engine = MockEngine { respond(bytes) }
            val output = TrackingOutputStream(failure, failAfter = 3)
            engine.use { engine ->
                KoniferBlockingClient.build("https://konifer.example", engine = engine).use { client ->
                    val result = client.assets("profile").originalVariant().fetchAndWriteContentTo(output)
                    result.shouldBeInstanceOf<KoniferResult.Failure.Transport>().cause.message shouldBe "disk full"
                }
                output.contents() shouldBe bytes.copyOf(3)
                output.flushes shouldBe 0
                output.closed shouldBe false
            }
        }

        test("cancellation propagates without flushing or closing the caller-owned output") {
            val engine = MockEngine { throw CancellationException("caller cancelled") }
            val output = TrackingOutputStream()
            engine.use { engine ->
                KoniferBlockingClient.build("https://konifer.example", engine = engine).use { client ->
                    shouldThrow<CancellationException> {
                        client.assets("profile").originalVariant().fetchAndWriteContentTo(output)
                    }
                }
                output.flushes shouldBe 0
                output.closed shouldBe false
            }
        }

        test("unexpected output exceptions propagate without closing the output") {
            val engine = MockEngine { respond(byteArrayOf(1, 2, 3)) }
            val output = TrackingOutputStream(IllegalStateException("unexpected failure"), failAfter = 0)
            engine.use { engine ->
                KoniferBlockingClient.build("https://konifer.example", engine = engine).use { client ->
                    shouldThrow<IllegalStateException> {
                        client.assets("profile").originalVariant().fetchAndWriteContentTo(output)
                    }.message shouldBe "unexpected failure"
                }
                output.flushes shouldBe 0
                output.closed shouldBe false
            }
        }
    })

private class TrackingOutputStream(
    private val failure: Exception? = null,
    private val failAfter: Int = Int.MAX_VALUE,
) : OutputStream() {
    private val buffer = ByteArrayOutputStream()
    var flushes: Int = 0
        private set
    var closed: Boolean = false
        private set

    override fun write(value: Int) {
        check(!closed) { "Stream was closed" }
        if (buffer.size() >= failAfter) throw checkNotNull(failure)
        buffer.write(value)
    }

    override fun flush() {
        flushes++
    }

    override fun close() {
        closed = true
    }

    fun contents(): ByteArray = buffer.toByteArray()
}
