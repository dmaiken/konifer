package asset.variant

import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpStatusCode
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import util.byteArrayToImage
import util.createJsonClient
import util.fetchAssetContent
import util.fetchAssetLink
import util.storeAsset

class VariantProfileTest {
    @Test
    fun `bad request returned when fetching asset variant with non-existent variant profile`() =
        testInMemory(
            """
            variant-profiles = [
                {
                    name = small
                    w = 10
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request, path = "profile")

            fetchAssetLink(client, path = "profile", profile = "medium", expectedStatusCode = HttpStatusCode.BadRequest)
        }

    @Test
    fun `can fetch variant with variant profile`() =
        testInMemory(
            """
            variant-profiles = [
                {
                    name = small
                    w = 10
                    mimeType = "image/jpeg"
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request, path = "profile")

            fetchAssetContent(client, path = "profile", profile = "small")!!.let { imageBytes ->
                val rendered = byteArrayToImage(imageBytes)
                rendered.width shouldBe 10
                rendered.height shouldNotBe bufferedImage.height
                Tika().detect(imageBytes) shouldBe "image/jpeg"
            }
        }

    @Test
    fun `can fetch variant with profile and overloaded image attributes`() =
        testInMemory(
            """
            variant-profiles = [
                {
                    name = small
                    w = 10
                    mimeType = "image/jpeg"
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request, path = "profile")

            fetchAssetContent(client, path = "profile", profile = "small", width = 100)!!.let { imageBytes ->
                val rendered = byteArrayToImage(imageBytes)
                rendered.width shouldBe 100
                rendered.height shouldNotBe bufferedImage.height
                Tika().detect(imageBytes) shouldBe "image/jpeg"
            }
        }
}
