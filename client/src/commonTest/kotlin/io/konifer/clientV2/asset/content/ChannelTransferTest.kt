package io.konifer.clientV2.asset.content

import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.KoniferV2Result
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class ChannelTransferTest :
    FunSpec({
        test("a transport failure before headers terminates the channel with the underlying failure") {
            val failure = IOException("offline")
            val engine = MockEngine { throw failure }
            val client = KoniferClientV2.build("https://konifer.example", engine = engine)
            val destination = ByteChannel()
            try {
                client.assets("profile").originalVariant().fetchAndWriteContentTo(destination) shouldBe
                    KoniferV2Result.Failure.Transport(failure)
                destination.isClosedForWrite shouldBe true
                destination.closedCause.shouldBeInstanceOf<IOException>().message shouldBe "offline"
                shouldThrow<IOException> { destination.toByteArray() }.message shouldBe "offline"
            } finally {
                client.close()
                engine.close()
            }
        }

        test("a partial response failure terminates the destination and wakes its reader") {
            withTimeout(5.seconds) {
                val bytes = byteArrayOf(1, 2, 3)
                val source = ByteChannel(autoFlush = true)
                source.writeFully(bytes)
                val engine = MockEngine { respond(source) }
                val client = KoniferClientV2.build("https://konifer.example", engine = engine)
                val destination = ByteChannel()
                val prefixReceived = CompletableDeferred<Unit>()
                val reader =
                    async {
                        destination.readByteArray(bytes.size) shouldBe bytes
                        prefixReceived.complete(Unit)
                        runCatching { destination.toByteArray() }
                    }
                val transfer = async { client.assets("profile").originalVariant().fetchAndWriteContentTo(destination) }
                try {
                    prefixReceived.await()
                    source.cancel(IOException("source failed"))
                    transfer
                        .await()
                        .shouldBeInstanceOf<KoniferV2Result.Failure.Transport>()
                        .cause.message shouldBe "source failed"
                    destination.isClosedForWrite shouldBe true
                    destination.closedCause.shouldBeInstanceOf<IOException>().message shouldBe "source failed"
                    reader
                        .await()
                        .exceptionOrNull()
                        .shouldBeInstanceOf<IOException>()
                        .message shouldBe "source failed"
                } finally {
                    transfer.cancel()
                    reader.cancel()
                    source.cancel(null)
                    destination.cancel(null)
                    client.close()
                    engine.close()
                }
            }
        }

        test("caller cancellation before headers is rethrown and terminates the destination") {
            withTimeout(5.seconds) {
                val requestStarted = CompletableDeferred<Unit>()
                val engine =
                    MockEngine {
                        requestStarted.complete(Unit)
                        awaitCancellation()
                    }
                val client = KoniferClientV2.build("https://konifer.example", engine = engine)
                val destination = ByteChannel()
                val transfer = async { client.assets("profile").originalVariant().fetchAndWriteContentTo(destination) }
                try {
                    requestStarted.await()
                    transfer.cancel(CancellationException("caller cancelled"))
                    shouldThrow<CancellationException> { transfer.await() }
                    destination.isClosedForWrite shouldBe true
                    destination.closedCause.shouldBeInstanceOf<CancellationException>().message shouldBe "caller cancelled"
                } finally {
                    transfer.cancel()
                    client.close()
                    engine.close()
                }
            }
        }

        test("unexpected request exceptions are rethrown and retained as the channel failure") {
            val engine = MockEngine { throw IllegalStateException("unexpected failure") }
            val client = KoniferClientV2.build("https://konifer.example", engine = engine)
            val destination = ByteChannel()
            try {
                shouldThrow<IllegalStateException> {
                    client.assets("profile").originalVariant().fetchAndWriteContentTo(destination)
                }.message shouldBe "unexpected failure"
                destination.isClosedForWrite shouldBe true
                destination.closedCause
                    .shouldBeInstanceOf<IOException>()
                    .cause
                    .shouldBeInstanceOf<IllegalStateException>()
                    .message shouldBe "unexpected failure"
            } finally {
                client.close()
                engine.close()
            }
        }
    })
