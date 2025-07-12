package asset

import asset.model.AssetClass
import asset.model.AssetResponse
import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import util.createJsonClient
import util.storeAsset

class AssetLifecycleTest {
    @Test
    fun `can create and get image`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val storeAssetResponse = storeAsset(client, image, request)
            storeAssetResponse!!.createdAt shouldNotBe null
            storeAssetResponse.variants.first().bucket shouldBe "assets"
            storeAssetResponse.variants.first().storeKey shouldNotBe null
            storeAssetResponse.variants.first().imageAttributes.mimeType shouldBe "image/png"
            storeAssetResponse.`class` shouldBe AssetClass.IMAGE
            storeAssetResponse.alt shouldBe "an image"
            storeAssetResponse.entryId shouldBe 0
            client.get("/assets/profile?return=metadata").apply {
                status shouldBe HttpStatusCode.OK
                body<AssetResponse>() shouldBe storeAssetResponse
            }
        }

    @Test
    fun `creating asset on same path results in most recent being fetched`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val entryIds = mutableListOf<Long>()
            repeat(2) {
                val response = storeAsset(client, image, request)
                entryIds.add(response!!.entryId)
            }
            entryIds shouldHaveSize 2
            client.get("/assets/profile?return=metadata").apply {
                status shouldBe HttpStatusCode.OK
                body<AssetResponse>().apply {
                    entryIds[1] shouldBe entryId
                    entryId shouldBe 1
                }
            }
        }
}
