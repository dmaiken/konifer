package io.asset

import io.asset.handler.AssetSource
import io.asset.model.AssetClass
import io.asset.model.StoreAssetRequest
import io.config.testInMemory
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.util.createJsonClient
import io.util.fetchAssetMetadata
import io.util.storeAssetMultipartSource
import org.junit.jupiter.api.Test

class AssetLifecycleTest {
    @Test
    fun `can create and get image`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val labels =
                mapOf(
                    "phone" to "iphone",
                    "type" to "vegetable",
                )
            val tags = setOf("smart", "cool")
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    labels = labels,
                    tags = tags,
                )
            val storeAssetResponse = storeAssetMultipartSource(client, image, request).second
            storeAssetResponse!!.createdAt shouldNotBe null
            storeAssetResponse.variants.first().bucket shouldBe "assets"
            storeAssetResponse.variants.first().storeKey shouldNotBe null
            storeAssetResponse.variants
                .first()
                .attributes.mimeType shouldBe "image/png"
            storeAssetResponse.`class` shouldBe AssetClass.IMAGE
            storeAssetResponse.alt shouldBe "an image"
            storeAssetResponse.entryId shouldBe 0
            storeAssetResponse.labels shouldContainExactly labels
            storeAssetResponse.tags shouldContainExactly tags
            storeAssetResponse.source shouldBe AssetSource.UPLOAD
            storeAssetResponse.sourceUrl shouldBe null
            storeAssetResponse.variants.forAll {
                it.storeKey shouldEndWith ".png"
            }
            fetchAssetMetadata(client, path = "profile") shouldBe storeAssetResponse
        }

    @Test
    fun `creating asset on same path results in most recent being fetched`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val entryIds = mutableListOf<Long>()
            repeat(2) {
                val response = storeAssetMultipartSource(client, image, request).second
                entryIds.add(response!!.entryId)
            }
            entryIds shouldHaveSize 2
            fetchAssetMetadata(client, path = "profile")!!.apply {
                entryId shouldBe entryIds[1]
            }
        }
}
