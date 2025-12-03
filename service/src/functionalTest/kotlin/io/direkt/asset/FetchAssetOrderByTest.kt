package io.direkt.asset

import io.direkt.asset.context.OrderBy
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.config.testInMemory
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAllAssetMetadata
import io.direkt.util.fetchAssetMetadata
import io.direkt.util.storeAssetMultipartSource
import io.direkt.util.updateAsset
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpHeaders
import org.junit.jupiter.api.Test

class FetchAssetOrderByTest {
    @Test
    fun `can fetch asset metadata and order by created at`() =
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
            val response1 =
                storeAssetMultipartSource(client, image, request, path = "profile").second.apply {
                    this shouldNotBe null
                }!!
            val response2 =
                storeAssetMultipartSource(client, image, request, path = "profile").apply {
                    this.second shouldNotBe null
                }
            val response3 =
                storeAssetMultipartSource(client, image, request, path = "profile").second.apply {
                    this shouldNotBe null
                }!!
            // Update response2
            updateAsset(
                client = client,
                location = response2.first[HttpHeaders.Location]!!,
                body = request.copy(alt = "I'm updated!!"),
            ).second.apply {
                this shouldNotBe null
            }!!
            fetchAssetMetadata(client, "profile", orderBy = OrderBy.CREATED)!!.apply {
                entryId shouldBe response3.entryId
            }

            fetchAllAssetMetadata(client, path = "profile", orderBy = OrderBy.CREATED, limit = 10).apply {
                size shouldBe 3
                get(0).entryId shouldBe response3.entryId
                get(1).entryId shouldBe response2.second!!.entryId
                get(2).entryId shouldBe response1.entryId
            }
        }

    @Test
    fun `can fetch asset metadata and order by modified at`() =
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
            val response1 =
                storeAssetMultipartSource(client, image, request, path = "profile").second.apply {
                    this shouldNotBe null
                }!!
            val response2 =
                storeAssetMultipartSource(client, image, request, path = "profile").apply {
                    this.second shouldNotBe null
                }
            val response3 =
                storeAssetMultipartSource(client, image, request, path = "profile").second.apply {
                    this shouldNotBe null
                }!!
            // Update response2
            val updated =
                updateAsset(
                    client = client,
                    location = response2.first[HttpHeaders.Location]!!,
                    body = request.copy(alt = "I'm updated!!"),
                ).second.apply {
                    this shouldNotBe null
                }!!
            fetchAssetMetadata(client, "profile", orderBy = OrderBy.MODIFIED)!!.apply {
                entryId shouldBe updated.entryId
            }

            fetchAllAssetMetadata(client, path = "profile", orderBy = OrderBy.MODIFIED, limit = 10).apply {
                size shouldBe 3
                get(0).entryId shouldBe updated.entryId
                get(1).entryId shouldBe response3.entryId
                get(2).entryId shouldBe response1.entryId
            }
        }
}
