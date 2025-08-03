package asset

import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import util.byteArrayToImage
import util.createJsonClient
import util.fetchAssetContent
import util.storeAsset
import java.util.UUID

class FetchAssetContentTest {
    @Test
    fun `fetching asset content that does not exist returns not found`() =
        testInMemory {
            val client = createJsonClient()
            fetchAssetContent(client, path = UUID.randomUUID().toString(), expectedStatusCode = HttpStatusCode.NotFound)
        }

    @Test
    fun `can fetch asset and render`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request, path = "profile")

            fetchAssetContent(client, path = "profile", expectedMimeType = "image/png")!!.let { imageBytes ->
                val rendered = byteArrayToImage(imageBytes)
                rendered.width shouldBe bufferedImage.width
                rendered.height shouldBe bufferedImage.height
                Tika().detect(imageBytes) shouldBe "image/png"
            }
        }
}
