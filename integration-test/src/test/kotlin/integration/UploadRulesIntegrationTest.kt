package integration

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.client.KoniferResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.apache.tika.Tika
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID

class UploadRulesIntegrationTest : BaseIntegrationTest() {
    @ParameterizedTest
    @EnumSource(value = ImageFormat::class)
    fun `can store asset that passes upload rules`(format: ImageFormat) {
        runBlocking {
            val path = UUID.randomUUID().toString()
            val (image, attributes) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE, format = format)
            val storeResponse =
                client.storeAsset(
                    path = "/accept/$path",
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
                    path = "/accept/$path",
                )
            fetchResponse::class shouldBe KoniferResponse.Success::class
            val content = (fetchResponse as KoniferResponse.Success).body
            Tika().detect(content) shouldBe format.mimeType

            Vips.run { arena ->
                val vImage = VImage.newFromBytes(arena, content)
                vImage.height shouldBe attributes.height
                vImage.width shouldBe attributes.width
            }
        }
    }
}
