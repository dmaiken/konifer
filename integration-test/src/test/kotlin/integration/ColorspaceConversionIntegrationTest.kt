package integration

import io.konifer.client.KoniferResult
import io.konifer.client.assets.fetch.requestedTransformation
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
                client
                    .assets(path)
                    .newAsset()
                    .fromBytes(
                        bytes = image,
                        format = attributes.format,
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
                            this.colorSpace = colorSpace
                        },
                    ).fetchContentBytes()
            fetchResponse::class shouldBe KoniferResult.Success::class
            val content = (fetchResponse as KoniferResult.Success).value
            tika.detect(content) shouldBe attributes.format.mimeType
        }
    }
}
