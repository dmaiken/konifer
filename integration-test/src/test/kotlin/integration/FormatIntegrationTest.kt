package integration

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.requestedTransformation
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
            for (destinationFormat in ImageFormat.entries) {
                if (sourceFormat == destinationFormat) continue

                tests.add(
                    dynamicTest("Converts ${sourceFormat.name} to ${destinationFormat.name}") {
                        runBlocking {
                            val path = UUID.randomUUID().toString()
                            val (image, attributes) = ImageFactory.testImage(sourceFormat)
                            val storeResponse =
                                clientV2
                                    .asset(path)
                                    .newAsset()
                                    .fromBytes(
                                        bytes = image,
                                        format = sourceFormat,
                                    ).withAlt("image")
                                    .withLabels(mapOf("key1" to "value1", "key2" to "value2"))
                                    .withTags(setOf("tag1", "tag2"))
                                    .store()
                            storeResponse::class shouldBe KoniferV2Result.Success::class

                            val fetchResponse =
                                clientV2
                                    .asset(path)
                                    .variant(
                                        requestedTransformation {
                                            format = destinationFormat
                                        },
                                    ).contentBytes()
                            val content = (fetchResponse as KoniferV2Result.Success).value
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
