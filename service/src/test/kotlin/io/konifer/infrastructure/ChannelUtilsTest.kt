package io.konifer.infrastructure

import io.konifer.ImageFactory
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ChannelUtilsTest {
    @Test
    fun `can tee a stream to two different channels`() =
        runTest {
            val output1Channel = ByteChannel()
            val output2Channel = ByteChannel()

            val output1 =
                async {
                    output1Channel.toByteArray()
                }
            val output2 =
                async {
                    output2Channel.toByteArray()
                }

            val input = ImageFactory.testImage().bytes
            val inputChannel = input.inputStream().toByteReadChannel()

            teeStream(
                source = inputChannel,
                firstChannel = output1Channel,
                secondChannel = output2Channel,
            )

            output1.await().size shouldBe input.size
            output2.await().size shouldBe input.size

            output1Channel.isClosedForWrite shouldBe true
            output2Channel.isClosedForWrite shouldBe true
        }

    @Test
    fun `can tee a stream to only one channel if second is null`() =
        runTest {
            val output1Channel = ByteChannel()

            val output1 =
                async {
                    output1Channel.toByteArray()
                }

            val input = ImageFactory.testImage().bytes
            val inputChannel = input.inputStream().toByteReadChannel()

            teeStream(
                source = inputChannel,
                firstChannel = output1Channel,
                secondChannel = null,
            )

            output1.await().size shouldBe input.size
            output1Channel.isClosedForWrite shouldBe true
        }
}
