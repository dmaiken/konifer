package io.direkt.infrastructure.vips

import com.vanniktech.blurhash.BlurHash
import io.direkt.domain.image.LQIPImplementation
import io.direkt.lqip.image.ThumbHash
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import io.matchers.shouldHaveSamePixelContentAs
import io.toBufferedImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

class ImagePreviewGeneratorTest {
    @Test
    fun `can generate blurhash from png`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val bufferedImage = ImageIO.read(ByteArrayInputStream(image))
            val imageChannel = ByteChannel(true)
            launch {
                ByteReadChannel(image).copyTo(imageChannel)
                imageChannel.close()
            }

            val previews = ImagePreviewGenerator.generatePreviews(imageChannel, setOf(LQIPImplementation.BLURHASH))

            previews.blurhash shouldNotBe null
            previews.thumbhash shouldBe null

            val actualBlurhash = BlurHash.decode(previews.blurhash!!, bufferedImage.width, bufferedImage.height)
            actualBlurhash shouldNotBe null

            val expected =
                this.javaClass.getResourceAsStream("/images/lqip/blurhash-1.png").use {
                    ImageIO.read(it)
                }
            actualBlurhash!! shouldHaveSamePixelContentAs expected
        }

    @Test
    fun `can generate thumbhash from png`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val imageChannel = ByteChannel(true)
            launch {
                ByteReadChannel(image).copyTo(imageChannel)
                imageChannel.close()
            }

            val previews = ImagePreviewGenerator.generatePreviews(imageChannel, setOf(LQIPImplementation.THUMBHASH))

            previews.blurhash shouldBe null
            previews.thumbhash shouldNotBe null

            val thumbhash =
                shouldNotThrowAny {
                    ThumbHash.thumbHashToRGBA(Base64.getDecoder().decode(previews.thumbhash))
                }
            val actualThumbhash =
                shouldNotThrowAny {
                    thumbhash.rgba.toBufferedImage(thumbhash.width, thumbhash.height)
                }

            val expectedThumbhash =
                this.javaClass.getResourceAsStream("/images/lqip/thumbhash-1.png").use {
                    ImageIO.read(it)
                }
            actualThumbhash shouldHaveSamePixelContentAs expectedThumbhash
        }

    @Test
    fun `can generate all previews if enabled`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val bufferedImage = ImageIO.read(ByteArrayInputStream(image))
            val imageChannel = ByteChannel(true)
            launch {
                ByteReadChannel(image).copyTo(imageChannel)
                imageChannel.close()
            }

            val previews =
                ImagePreviewGenerator.generatePreviews(
                    imageChannel,
                    setOf(LQIPImplementation.THUMBHASH, LQIPImplementation.BLURHASH),
                )

            previews.blurhash shouldNotBe null
            previews.thumbhash shouldNotBe null

            val thumbhash =
                shouldNotThrowAny {
                    ThumbHash.thumbHashToRGBA(Base64.getDecoder().decode(previews.thumbhash))
                }
            val actualThumbhash =
                shouldNotThrowAny {
                    thumbhash.rgba.toBufferedImage(thumbhash.width, thumbhash.height)
                }

            val expectedThumbhash =
                this.javaClass.getResourceAsStream("/images/lqip/thumbhash-1.png").use {
                    ImageIO.read(it)
                }
            val actualBlurhash = BlurHash.decode(previews.blurhash!!, bufferedImage.width, bufferedImage.height)
            actualBlurhash shouldNotBe null

            val expectedBlurhash =
                this.javaClass.getResourceAsStream("/images/lqip/blurhash-1.png").use {
                    ImageIO.read(it)
                }
            actualThumbhash shouldHaveSamePixelContentAs expectedThumbhash
            actualBlurhash!! shouldHaveSamePixelContentAs expectedBlurhash
        }

    @Test
    fun `If no image channel then no previews are generated`() =
        runTest {
            val previews = ImagePreviewGenerator.generatePreviews(null, setOf(LQIPImplementation.THUMBHASH))

            previews.blurhash shouldBe null
            previews.thumbhash shouldBe null
        }

    @Test
    fun `If no previews enabled in path configuration then no previews are generated`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val imageChannel = ByteChannel(true)
            launch {
                ByteReadChannel(image).copyTo(imageChannel)
                imageChannel.close()
            }
            val previews = ImagePreviewGenerator.generatePreviews(imageChannel, emptySet())

            previews.blurhash shouldBe null
            previews.thumbhash shouldBe null
        }

    @Test
    fun `cannot create preview on image that is too large`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val imageChannel = ByteChannel(true)
            launch {
                ByteReadChannel(image).copyTo(imageChannel)
                imageChannel.close()
            }
            val exception =
                shouldThrow<IllegalArgumentException> {
                    ImagePreviewGenerator.generatePreviews(imageChannel, setOf(LQIPImplementation.BLURHASH))
                }
            exception.message shouldBe
                "Image must be smaller than ${ImagePreviewGenerator.MAX_WIDTH}x${ImagePreviewGenerator.MAX_HEIGHT} to generate previews"
        }
}
