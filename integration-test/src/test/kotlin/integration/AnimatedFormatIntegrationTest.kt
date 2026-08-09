package integration

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import io.konifer.client.KoniferResponse
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.UUID

class AnimatedFormatIntegrationTest : BaseIntegrationTest() {
    @TestFactory
    fun `can request animated asset and convert all pages to supported animated format`(): List<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        val supportedAnimatedFormats = listOf(ImageFormat.GIF, ImageFormat.WEBP)
        for (sourceFormat in supportedAnimatedFormats) {
            for (destinationFormat in supportedAnimatedFormats) {
                if (sourceFormat == destinationFormat) continue

                tests.add(
                    dynamicTest("Converts animated ${sourceFormat.name} to animated ${destinationFormat.name}") {
                        runBlocking {
                            val path = UUID.randomUUID().toString()
                            val (image, attributes) = ImageFactory.testImage(sourceFormat, type = TestImageType.KERMIT)
                            val storeResponse =
                                client.storeAsset(
                                    path = path,
                                    format = sourceFormat,
                                    bytes = image,
                                    request =
                                        StoreAssetRequest(
                                            alt = "image",
                                            tags = setOf("tag1", "tag2"),
                                            labels = mapOf("key1" to "value1", "key2" to "value2"),
                                        ),
                                )
                            storeResponse::class shouldBe KoniferResponse.Success::class

                            val fetchResponse =
                                client.fetchAssetContentBytes(
                                    path = path,
                                    requestedTransformation =
                                        requestedTransformation {
                                            format = destinationFormat
                                        },
                                )
                            fetchResponse::class shouldBe KoniferResponse.Success::class
                            val content = (fetchResponse as KoniferResponse.Success).body
                            tika.detect(content) shouldBe destinationFormat.mimeType

                            Vips.run { arena ->
                                val vImage = VImage.newFromBytes(arena, content, VipsOption.Int("n", -1))
                                vImage.height shouldBe attributes.height
                                vImage.width shouldBe attributes.width
                                vImage.getInt("n-pages") shouldBeGreaterThan 1 shouldBe attributes.pages
                            }
                        }
                    },
                )
            }
        }
        return tests
    }

    @TestFactory
    fun `can request animated asset and convert to non-animated format which retains the first page`(): List<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        val supportedAnimatedFormats = listOf(ImageFormat.GIF, ImageFormat.WEBP)
        for (sourceFormat in supportedAnimatedFormats) {
            for (destinationFormat in ImageFormat.entries) {
                if (destinationFormat in supportedAnimatedFormats) continue

                tests.add(
                    dynamicTest("Converts animated ${sourceFormat.name} to non-animated ${destinationFormat.name}") {
                        runBlocking {
                            val path = UUID.randomUUID().toString()
                            val (image, attributes) = ImageFactory.testImage(sourceFormat, type = TestImageType.KERMIT)
                            val storeResponse =
                                client.storeAsset(
                                    path = path,
                                    format = sourceFormat,
                                    bytes = image,
                                    request =
                                        StoreAssetRequest(
                                            alt = "image",
                                            tags = setOf("tag1", "tag2"),
                                            labels = mapOf("key1" to "value1", "key2" to "value2"),
                                        ),
                                )
                            storeResponse::class shouldBe KoniferResponse.Success::class

                            val fetchResponse =
                                client.fetchAssetContentBytes(
                                    path = path,
                                    requestedTransformation =
                                        requestedTransformation {
                                            format = destinationFormat
                                        },
                                )
                            fetchResponse::class shouldBe KoniferResponse.Success::class
                            val content = (fetchResponse as KoniferResponse.Success).body
                            tika.detect(content) shouldBe destinationFormat.mimeType

                            Vips.run { arena ->
                                val vImage = VImage.newFromBytes(arena, content, VipsOption.Int("n", -1))
                                vImage.height shouldBe attributes.height / attributes.pages
                                vImage.width shouldBe attributes.width
                                (vImage.getInt("n-pages") ?: 1) shouldBe 1
                            }
                        }
                    },
                )
            }
        }
        return tests
    }
}
