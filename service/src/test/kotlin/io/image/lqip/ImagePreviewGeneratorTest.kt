package io.image.lqip

import com.vanniktech.blurhash.BlurHash
import io.aws.S3Properties
import io.image.lqip.ImagePreviewGenerator.Companion.MAX_HEIGHT
import io.image.lqip.ImagePreviewGenerator.Companion.MAX_WIDTH
import io.image.model.ImageProperties
import io.image.model.PreProcessingProperties
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import io.lqip.image.ThumbHash
import io.matchers.shouldHaveSamePixelContentAs
import io.path.configuration.PathConfiguration
import io.toBufferedImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

class ImagePreviewGeneratorTest {
    private val previewGenerator = ImagePreviewGenerator()

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
            val pathConfiguration = generatePathConfiguration(setOf(LQIPImplementation.BLURHASH))

            val previews = previewGenerator.generatePreviews(imageChannel, pathConfiguration)

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
            val pathConfiguration = generatePathConfiguration(setOf(LQIPImplementation.THUMBHASH))

            val previews = previewGenerator.generatePreviews(imageChannel, pathConfiguration)

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
            val pathConfiguration = generatePathConfiguration(setOf(LQIPImplementation.THUMBHASH, LQIPImplementation.BLURHASH))

            val previews = previewGenerator.generatePreviews(imageChannel, pathConfiguration)

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
            val pathConfiguration = generatePathConfiguration(setOf(LQIPImplementation.THUMBHASH))

            val previews = previewGenerator.generatePreviews(null, pathConfiguration)

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
            val pathConfiguration = generatePathConfiguration(setOf())

            val previews = previewGenerator.generatePreviews(imageChannel, pathConfiguration)

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
            val pathConfiguration = generatePathConfiguration(setOf(LQIPImplementation.BLURHASH))

            val exception =
                shouldThrow<IllegalArgumentException> {
                    previewGenerator.generatePreviews(imageChannel, pathConfiguration)
                }
            exception.message shouldBe "Image must be smaller than ${MAX_WIDTH}x$MAX_HEIGHT to generate previews"
        }

    private fun generatePathConfiguration(previews: Set<LQIPImplementation>): PathConfiguration =
        PathConfiguration.create(
            allowedContentTypes = null,
            imageProperties =
                ImageProperties.create(
                    preProcessing = PreProcessingProperties.DEFAULT,
                    lqip = previews,
                ),
            eagerVariants = emptyList(),
            s3Properties = S3Properties.DEFAULT,
        )
}
