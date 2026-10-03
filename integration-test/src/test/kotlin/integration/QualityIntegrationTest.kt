package integration

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.client.KoniferResult
import io.konifer.client.assets.fetch.requestedTransformation
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
                                client
                                    .assets(path)
                                    .newAsset()
                                    .fromBytes(
                                        bytes = image,
                                        format = format,
                                    ).withAlt("image")
                                    .withLabels(mapOf("key1" to "value1", "key2" to "value2"))
                                    .withTags(setOf("tag1", "tag2"))
                                    .store()
                            storeResponse::class shouldBe KoniferResult.Success::class

                            val fetchResponse =
                                client
                                    .assets(path)
                                    .variant(
                                        requestedTransformation {
                                            this.quality = quality
                                        },
                                    ).fetchContentBytes()
                            fetchResponse::class shouldBe KoniferResult.Success::class
                            val content = (fetchResponse as KoniferResult.Success).value
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
