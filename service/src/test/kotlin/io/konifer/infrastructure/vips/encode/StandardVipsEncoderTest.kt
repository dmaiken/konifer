package io.konifer.infrastructure.vips.encode

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsError
import app.photofox.vipsffm.enums.VipsBandFormat
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByte
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import kotlin.random.Random

@Timeout(30)
class StandardVipsEncoderTest {
    @ParameterizedTest
    @EnumSource(ImageFormat::class, names = ["JPEG", "PNG", "WEBP"])
    fun `streams the same encoded bytes as the OutputStream target and closes the channel`(format: ImageFormat) =
        runBlocking {
            Vips.run { arena ->
                val source = source(arena)
                val transformation = transformation(format)
                val expected = encodeToBytes(source, transformation)
                val output = ByteChannel()
                val received = async(Dispatchers.IO) { output.toByteArray() }

                try {
                    StandardVipsEncoder.writeToStream(arena, source, transformation, output)

                    output.isClosedForWrite shouldBe true
                    runBlocking { received.await() } shouldBe expected
                    output.closedCause shouldBe null
                } finally {
                    output.cancel(null)
                }
            }
        }

    @Test
    fun `preserves native output while a slow reader applies backpressure`() =
        runBlocking {
            Vips.run { arena ->
                val source = source(arena, width = 1024, height = 512)
                val transformation = transformation(ImageFormat.PNG, width = 1024, height = 512)
                val expected = encodeToBytes(source, transformation)
                // Exceed Ktor's channel capacity so writes must wait for the reader.
                expected.size shouldBeGreaterThan (1024 * 1024)
                val output = ByteChannel()
                val received =
                    async(Dispatchers.IO) {
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = output.readAvailable(buffer)
                            if (count == -1) break
                            bytes.write(buffer, 0, count)
                            delay(1)
                        }
                        bytes.toByteArray()
                    }

                try {
                    StandardVipsEncoder.writeToStream(arena, source, transformation, output)

                    runBlocking { received.await() } shouldBe expected
                    output.isClosedForWrite shouldBe true
                    output.closedCause shouldBe null
                } finally {
                    output.cancel(null)
                }
            }
        }

    @Test
    fun `propagates consumer failure after output has started without escaping the native callback`() =
        runBlocking {
            Vips.run { arena ->
                val output = ByteChannel()
                val failure = IOException("consumer failed")
                val consumer =
                    launch(Dispatchers.IO) {
                        output.readByte()
                        output.cancel(failure)
                    }

                try {
                    val error =
                        shouldThrow<ClosedByteChannelException> {
                            StandardVipsEncoder.writeToStream(
                                arena,
                                source(arena, width = 1024, height = 512),
                                transformation(ImageFormat.PNG, width = 1024, height = 512),
                                output,
                            )
                        }

                    error.cause shouldBeSameInstanceAs failure
                    output.isClosedForWrite shouldBe true
                    runBlocking { consumer.join() }
                } finally {
                    output.cancel(null)
                }
            }
        }

    @Test
    fun `propagates consumer cancellation without escaping the native callback`() {
        Vips.run { arena ->
            val output = ByteChannel()
            val failure = CancellationException("consumer cancelled")
            output.cancel(failure)

            shouldThrow<CancellationException> {
                StandardVipsEncoder.writeToStream(arena, source(arena), transformation(ImageFormat.PNG), output)
            }.cause shouldBeSameInstanceAs failure

            output.isClosedForWrite shouldBe true
        }
    }

    @Test
    fun `cancels the output channel when the native encoder fails before writing`() {
        Vips.run { arena ->
            val output = ByteChannel()
            // Raw pixels require their dedicated encoder; no standard saver accepts .bin.
            val failure =
                shouldThrow<VipsError> {
                    StandardVipsEncoder.writeToStream(arena, source(arena), transformation(ImageFormat.PIXELS), output)
                }

            output.isClosedForWrite shouldBe true
            output.closedCause?.cause shouldBeSameInstanceAs failure
        }
    }

    private fun source(
        arena: Arena,
        width: Int = 256,
        height: Int = 256,
    ): VImage {
        val pixels = Random(42).nextBytes(width * height * 3)
        return VImage.newFromMemory(
            arena,
            arena.allocateFrom(ValueLayout.JAVA_BYTE, *pixels),
            width,
            height,
            3,
            VipsBandFormat.FORMAT_UCHAR.rawValue,
        )
    }

    private fun transformation(
        format: ImageFormat,
        width: Int = 256,
        height: Int = 256,
    ) = Transformation(
        width = width.toDimension(),
        height = height.toDimension(),
        format = format,
        colorSpace = ColorSpace.SRGB,
    )

    private fun encodeToBytes(
        source: VImage,
        transformation: Transformation,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        source.writeToStream(
            output,
            transformation.format.extension,
            *constructEncoderOptions(transformation.format, transformation.quality.value),
        )
        return output.toByteArray()
    }
}
