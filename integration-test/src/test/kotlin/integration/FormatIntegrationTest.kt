package integration

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.client.KoniferResponse
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.apache.tika.Tika
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.UUID

class FormatIntegrationTest : BaseIntegrationTest() {
    @TestFactory
    fun `can request asset in supported formats`(): List<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        for (sourceFormat in ImageFormat.entries) {
            for (destinationFormat in ImageFormat.entries) {
                if (sourceFormat == destinationFormat) continue

                tests.add(
                    dynamicTest("Converts ${sourceFormat.name} to ${destinationFormat.name}") {
                        runBlocking {
                            val path = UUID.randomUUID().toString()
                            val (image, attributes) = ImageFactory.testImage(sourceFormat)
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
                            Tika().detect(content) shouldBe destinationFormat.mimeType

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
