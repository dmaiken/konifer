package integration

import io.konifer.client.KoniferResponse
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.TransformableColorSpace
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.apache.tika.Tika
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
            val storeResponse = client.storeAsset(
                path = path,
                format = attributes.format,
                bytes = image,
                request = StoreAssetRequest(
                    alt = "image",
                    tags = setOf("tag1", "tag2"),
                    labels = mapOf("key1" to "value1", "key2" to "value2")
                )
            )
            storeResponse::class shouldBe KoniferResponse.Success::class
            val fetchResponse = client.fetchAssetContentBytes(
                path = path,
                requestedTransformation = requestedTransformation {
                    this.colorSpace = colorSpace
                }
            )
            fetchResponse::class shouldBe KoniferResponse.Success::class
            val content = (fetchResponse as KoniferResponse.Success).body
            Tika().detect(content) shouldBe attributes.format.mimeType
        }
    }
}
