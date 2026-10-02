package integration

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.requestedTransformation
import io.konifer.common.image.TransformableColorSpace
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID

class ColorspaceConversionIntegrationTest : BaseIntegrationTest() {
    @ParameterizedTest
    @EnumSource(TransformableColorSpace::class)
    fun `can convert to color space`(colorSpace: TransformableColorSpace) {
        runBlocking {
            val path = UUID.randomUUID().toString()
            val (image, attributes) = ImageFactory.testImage()
            val storeResponse =
                clientV2
                    .asset(path)
                    .newAsset()
                    .fromBytes(
                        bytes = image,
                        format = attributes.format,
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
                            this.colorSpace = colorSpace
                        },
                    ).contentBytes()
            fetchResponse::class shouldBe KoniferV2Result.Success::class
            val content = (fetchResponse as KoniferV2Result.Success).value
            tika.detect(content) shouldBe attributes.format.mimeType
        }
    }
}
