package io.direkt.asset

import io.direkt.asset.model.StoreAssetRequest
import io.direkt.config.testInMemory
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetMetadata
import io.direkt.util.storeAssetMultipartSource
import io.direkt.util.updateAsset
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class UpdateAssetTest {
    @Test
    fun `can update asset metadata`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    labels =
                        mapOf(
                            "phone" to "iphone",
                            "type" to "vegetable",
                        ),
                    tags = setOf("smart", "cool"),
                )
            val (storeResponseHeaders, storeAssetResponse) = storeAssetMultipartSource(client, image, request)
            storeAssetResponse!!.alt shouldBe "an image"
            storeAssetResponse.entryId shouldBe 0
            storeAssetResponse.labels shouldContainExactly request.labels
            storeAssetResponse.tags shouldContainExactly request.tags
            fetchAssetMetadata(client, path = "profile") shouldBe storeAssetResponse

            val updateRequest =
                StoreAssetRequest(
                    tags = setOf("smarter", "cooler"),
                    labels = mapOf("phone" to "android", "fruit" to "banana"),
                    alt = "a bad image",
                )
            val (_, updateResponse) = updateAsset(client, storeResponseHeaders[HttpHeaders.Location]!!, updateRequest)
            updateResponse shouldNotBe null
            updateResponse!!.entryId shouldBe 0
            updateResponse.labels shouldContainExactly updateRequest.labels
            updateResponse.tags shouldContainExactly updateRequest.tags
            updateResponse.alt shouldBe updateRequest.alt
            updateResponse.createdAt shouldBe storeAssetResponse.createdAt
            updateResponse.modifiedAt shouldBeAfter storeAssetResponse.modifiedAt

            fetchAssetMetadata(client, path = "profile") shouldBe updateResponse
        }

    @Test
    fun `can update asset metadata to remove fields`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    labels =
                        mapOf(
                            "phone" to "iphone",
                            "type" to "vegetable",
                        ),
                    tags = setOf("smart", "cool"),
                )
            val (storeResponseHeaders, storeAssetResponse) = storeAssetMultipartSource(client, image, request)
            storeAssetResponse!!.alt shouldBe "an image"
            storeAssetResponse.entryId shouldBe 0
            storeAssetResponse.labels shouldContainExactly request.labels
            storeAssetResponse.tags shouldContainExactly request.tags
            fetchAssetMetadata(client, path = "profile") shouldBe storeAssetResponse

            val updateRequest =
                StoreAssetRequest(
                    tags = setOf(),
                    labels = mapOf(),
                    alt = null,
                )
            val (_, updateResponse) = updateAsset(client, storeResponseHeaders[HttpHeaders.Location]!!, updateRequest)
            updateResponse shouldNotBe null
            updateResponse!!.entryId shouldBe 0
            updateResponse.labels shouldContainExactly updateRequest.labels
            updateResponse.tags shouldContainExactly updateRequest.tags
            updateResponse.alt shouldBe updateRequest.alt

            fetchAssetMetadata(client, path = "profile") shouldBe updateResponse
        }

    @Test
    fun `updating asset that does not exists returns not found`() =
        testInMemory {
            val client = createJsonClient()
            val updateRequest =
                StoreAssetRequest(
                    tags = setOf(),
                    labels = mapOf(),
                    alt = null,
                )
            updateAsset(
                client = client,
                location = "https://localhost/assets/profile/bad/path/-/entry/1",
                body = updateRequest,
                expectedStatusCode = HttpStatusCode.NotFound,
            )
        }

    @Test
    fun `cannot update asset without entryId`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    labels =
                        mapOf(
                            "phone" to "iphone",
                            "type" to "vegetable",
                        ),
                    tags = setOf("smart", "cool"),
                )
            val (storeResponseHeaders, storeAssetResponse) = storeAssetMultipartSource(client, image, request)
            storeAssetResponse!!.alt shouldBe "an image"
            storeAssetResponse.entryId shouldBe 0
            storeAssetResponse.labels shouldContainExactly request.labels
            storeAssetResponse.tags shouldContainExactly request.tags
            fetchAssetMetadata(client, path = "profile") shouldBe storeAssetResponse

            val updateRequest =
                StoreAssetRequest(
                    tags = setOf(),
                    labels = mapOf(),
                    alt = null,
                )
            val locationWithoutEntryId = storeResponseHeaders[HttpHeaders.Location]!!.removeSuffix("/entry/${storeAssetResponse.entryId}")
            updateAsset(client, locationWithoutEntryId, updateRequest, expectedStatusCode = HttpStatusCode.BadRequest)

            fetchAssetMetadata(client, path = "profile") shouldBe storeAssetResponse
        }
}
