package io.asset

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.apache.tika.Tika
import org.junit.jupiter.api.Test

class AssetStreamContainerTest {
    @Test
    fun `can read all content from stream`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val imageChannel = ByteReadChannel(image)

            AssetStreamContainer(imageChannel).use { container ->
                val streamed = container.readNBytes(image.size, false)

                Tika().detect(image) shouldBe "image/png"
                Tika().detect(streamed) shouldBe "image/png"

                streamed shouldBe image
            }
        }

    @Test
    fun `can read buffer content from stream and buffer within the container`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val imageChannel = ByteReadChannel(image)

            AssetStreamContainer(imageChannel).use { container ->
                val header = container.readNBytes(1024, true)

                header shouldBe container.readNBytes(1024, false)
                header + container.readNBytes(image.size, false) shouldBe image
            }
        }

    @Test
    fun `can read some content from stream`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val expected = ByteArray(2048)
            image.copyInto(expected, endIndex = expected.size)
            val imageChannel = ByteReadChannel(image)

            AssetStreamContainer(imageChannel).use { container ->
                val streamed = container.readNBytes(2048, false)

                streamed shouldBe expected
            }
        }

    @Test
    fun `reading more than the amount of data in the stream returns the entire content`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val imageChannel = ByteReadChannel(image)

            AssetStreamContainer(imageChannel).use { container ->
                val streamed = container.readNBytes(image.size + 1000, true)

                streamed shouldBe image
            }
        }

    @Test
    fun `content is not double buffered`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val imageChannel = ByteReadChannel(image)

            AssetStreamContainer(imageChannel).use { container ->
                val header = container.readNBytes(1024, true)
                val header2 = container.readNBytes(1024, true)
                val header3 = container.readNBytes(1024, true)

                header shouldBe header2 shouldBe header3

                container.readNBytes(2048, true)

                val streamed = container.readNBytes(image.size, false)
                streamed shouldBe image
            }
        }

    @Test
    fun `content is not double buffered if second call specifies no buffering of result`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val imageChannel = ByteReadChannel(image)

            AssetStreamContainer(imageChannel).use { container ->
                val header = container.readNBytes(1024, true)
                val header2 = container.readNBytes(1024, false)

                header shouldBe header2
            }
        }

    @Test
    fun `when content read exceeds maxBytes then exception is thrown`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val imageChannel = ByteReadChannel(image)

            AssetStreamContainer(imageChannel, maxBytes = 8092).use { container ->
                container.readNBytes(8092, false)

                shouldThrow<IllegalArgumentException> {
                    container.readNBytes(1, false)
                }
            }
        }
}
