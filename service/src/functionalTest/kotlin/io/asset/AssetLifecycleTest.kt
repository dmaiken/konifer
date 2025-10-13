package io.asset

import io.asset.model.AssetClass
import io.asset.model.StoreAssetRequest
import io.config.testInMemory
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.util.createJsonClient
import io.util.fetchAssetInfo
import io.util.storeAsset
import org.junit.jupiter.api.Test

class AssetLifecycleTest {
    @Test
    fun `can create and get image`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
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
            fetchAssetInfo(client, path = "profile") shouldBe storeAssetResponse
        }

    @Test
    fun `creating asset on same path results in most recent being fetched`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
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
            fetchAssetInfo(client, path = "profile")!!.apply {
                entryId shouldBe entryIds[1]
            }
        }
}
