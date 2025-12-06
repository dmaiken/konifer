package io.direkt.asset

import io.direkt.config.testInMemory
import io.direkt.domain.asset.AssetClass
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAllAssetMetadata
import io.direkt.util.fetchAssetMetadata
import io.direkt.util.storeAssetMultipartSource
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import java.util.UUID

class FetchAssetMetadataTest {
    @Test
    fun `getting all asset info with path returns all info`() =
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
            val entryIds = mutableListOf<Long>()
            repeat(2) {
                storeAssetMultipartSource(client, image, request, path = "profile").second?.apply {
                    entryIds.add(entryId)
                }
            }
            entryIds shouldHaveSize 2
            fetchAssetMetadata(client, "profile")!!.apply {
                entryId shouldBe entryIds[1]
                tags shouldContainExactly tags
                labels shouldContainExactly labels
                alt shouldBe request.alt
                variants shouldHaveSize 1
                `class` shouldBe AssetClass.IMAGE
            }

            fetchAllAssetMetadata(client, path = "profile", limit = 10).apply {
                size shouldBe 2
                get(0).entryId shouldBe entryIds[1]
                get(1).entryId shouldBe entryIds[0]
                this.forAll {
                    it.tags shouldContainExactly tags
                    it.labels shouldContainExactly labels
                    it.alt shouldBe request.alt
                    it.variants shouldHaveSize 1
                    it.`class` shouldBe AssetClass.IMAGE
                }
            }
        }

    @Test
    fun `fetching info of asset that does not exist returns not found`() =
        testInMemory {
            val client = createJsonClient()
            fetchAssetMetadata(client, UUID.randomUUID().toString(), expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `fetching info of asset path that does not contain any assets returns not found`() =
        testInMemory {
            val client = createJsonClient()
            fetchAllAssetMetadata(client, UUID.randomUUID().toString(), limit = 10) shouldHaveSize 0
        }
}
