package io.direkt.asset.variant

import io.byteArrayToImage
import io.direkt.config.testInMemory
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetContent
import io.direkt.util.fetchAssetLink
import io.direkt.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpStatusCode
import org.apache.tika.Tika
import org.junit.jupiter.api.Test

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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile")

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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile")

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
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile")

            fetchAssetContent(client, path = "profile", profile = "small", width = 100)!!.let { imageBytes ->
                val rendered = byteArrayToImage(imageBytes)
                rendered.width shouldBe 100
                rendered.height shouldNotBe bufferedImage.height
                Tika().detect(imageBytes) shouldBe "image/jpeg"
            }
        }
}
