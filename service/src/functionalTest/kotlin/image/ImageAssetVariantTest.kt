package image

import asset.model.AssetClass
import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import util.byteArrayToImage
import util.createJsonClient
import util.storeAsset

class ImageAssetVariantTest {
    @Test
    fun `can fetch image variant by height`() =
        testInMemory {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val originalScale = bufferedImage.width.toDouble() / bufferedImage.height.toDouble()

            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request)!!.apply {
                createdAt shouldNotBe null
                alt shouldBe "an image"
                `class` shouldBe AssetClass.IMAGE

                variants.apply {
                    size shouldBe 1
                }
            }
        }

    @Test
    fun `can fetch image variant by width`() =
        testInMemory {
        }

    @Test
    fun `can fetch image variant by height and width and the aspect ratio is respected`() =
        testInMemory {
        }

    @Test
    fun `can fetch image variant by content type`() =
        testInMemory {
        }
}
