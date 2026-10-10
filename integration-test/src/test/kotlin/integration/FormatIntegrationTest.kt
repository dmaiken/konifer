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

class FormatIntegrationTest : BaseIntegrationTest() {
    @TestFactory
    fun `can request asset in supported formats`(): List<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        for (sourceFormat in ImageFormat.entries) {
            // PIXEL source input is not supported
            if (sourceFormat == ImageFormat.PIXELS) continue
            for (destinationFormat in ImageFormat.entries) {
                if (sourceFormat == destinationFormat) continue

                tests.add(
                    dynamicTest("Converts ${sourceFormat.name} to ${destinationFormat.name}") {
                        runBlocking {
                            val path = UUID.randomUUID().toString()
                            val (image, attributes) = ImageFactory.testImage(sourceFormat)
                            val storeResponse =
                                client
                                    .assets(path)
                                    .newAsset()
                                    .fromBytes(
                                        bytes = image,
                                        format = sourceFormat,
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
                                            format = destinationFormat
                                        },
                                    ).fetchContentBytes()
                            val content = (fetchResponse as KoniferResult.Success).value
                            tika.detect(content) shouldBe destinationFormat.mimeType

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
