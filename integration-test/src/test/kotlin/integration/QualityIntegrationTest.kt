package integration

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.client.KoniferResponse
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.UUID

class QualityIntegrationTest : BaseIntegrationTest() {
    @TestFactory
    fun `can request variant in specified qualities`(): List<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        for (format in ImageFormat.entries) {
            listOf(1, 100).forEach { quality ->
                tests.add(
                    dynamicTest("can request ${format.name} with quality $quality") {
                        runBlocking {
                            val path = UUID.randomUUID().toString()
                            val (image, attributes) = ImageFactory.testImage(format)
                            val storeResponse =
                                client.storeAsset(
                                    path = path,
                                    format = format,
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
                                            this.quality = quality
                                        },
                                )
                            fetchResponse::class shouldBe KoniferResponse.Success::class
                            val content = (fetchResponse as KoniferResponse.Success).body
                            tika.detect(content) shouldBe format.mimeType

                            Vips.run { arena ->
                                val vImage = VImage.newFromBytes(arena, content)
                                vImage.height shouldBe attributes.height
                                vImage.width shouldBe attributes.width
                            }
                        }
                    },
                )
            }
        }
        return tests
    }
}
