package asset.variant

import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.common.runBlocking
import io.kotest.inspectors.forAtLeast
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import util.createJsonClient
import util.fetchAssetInfo
import util.storeAsset
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class EagerVariantTest {
    @Test
    fun `can store asset and eager variannts are generated`() =
        testInMemory(
            """
            variant-profiles = [
                {
                    name = small
                    w = 15
                },
                {
                    name = medium
                    h = 15
                }
            ]
            path-configuration = [
                {
                    path = "/users/**"
                    eager-variants = [small, medium]
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = ImageIO.read(ByteArrayInputStream(image))
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val storeResponse = storeAsset(client, image, request, path = "users/123")

            // eager variants should not be in this list
            storeResponse!!.variants shouldHaveSize 1

            await().untilCallTo {
                runBlocking {
                    fetchAssetInfo(client, "users/123")!!.variants.size
                }
            } matches { count -> count == 3 }

            val variants = fetchAssetInfo(client, "users/123")!!.variants
            variants.forExactly(1) {
                it.imageAttributes.height shouldBe 15
                it.imageAttributes.width shouldNotBe 15
            }
            variants.forExactly(1) {
                it.imageAttributes.height shouldNotBe 15
                it.imageAttributes.width shouldBe 15
            }
            variants.forAtLeast(1) {
                it.imageAttributes.height shouldBe bufferedImage.height
                it.imageAttributes.width shouldBe bufferedImage.width
            }
        }
}
