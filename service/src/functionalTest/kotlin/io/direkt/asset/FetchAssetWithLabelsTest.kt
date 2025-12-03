package io.direkt.asset

import io.direkt.asset.model.AssetClass
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.config.testInMemory
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetMetadata
import io.direkt.util.storeAssetMultipartSource
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class FetchAssetWithLabelsTest {
    @Test
    fun `can fetch asset with labels`() =
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
            val requestWithoutLabels =
                StoreAssetRequest(
                    alt = "an image",
                    tags = tags,
                )
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")
            val response = storeAssetMultipartSource(client, image, request, path = "profile").second!!
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")

            fetchAssetMetadata(client, "profile", labels = labels)!!.apply {
                tags shouldContainExactly tags
                labels shouldContainExactly labels
                alt shouldBe request.alt
                variants shouldHaveSize 1
                `class` shouldBe AssetClass.IMAGE
                entryId shouldBe response.entryId
            }
        }

    @Test
    fun `can fetch asset with labels and entryId`() =
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
            val requestWithoutLabels =
                StoreAssetRequest(
                    alt = "an image",
                    tags = tags,
                )
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")
            val response = storeAssetMultipartSource(client, image, request, path = "profile").second!!
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")

            fetchAssetMetadata(client, "profile", entryId = response.entryId, labels = labels)!!.apply {
                tags shouldContainExactly tags
                labels shouldContainExactly labels
                alt shouldBe request.alt
                variants shouldHaveSize 1
                `class` shouldBe AssetClass.IMAGE
                entryId shouldBe response.entryId
            }

            // Verify wrong entryId with right labels returns NotFound
            fetchAssetMetadata(client, "profile", entryId = response.entryId + 1, labels = labels, expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `can fetch asset with namespaced labels`() =
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
            val requestWithoutLabels =
                StoreAssetRequest(
                    alt = "an image",
                    tags = tags,
                )
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")
            val response = storeAssetMultipartSource(client, image, request, path = "profile").second!!
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")

            fetchAssetMetadata(client, "profile", labels = labels.mapKeys { "label:${it.key}" })!!.apply {
                tags shouldContainExactly tags
                labels shouldContainExactly labels
                alt shouldBe request.alt
                variants shouldHaveSize 1
                `class` shouldBe AssetClass.IMAGE
                entryId shouldBe response.entryId
            }
        }

    @Test
    fun `can fetch asset with namespaced labels overloading variant transformation parameters`() =
        testInMemory {
            val client = createJsonClient()

            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val labels =
                mapOf(
                    "bg" to "iphone",
                    "w" to "vegetable",
                )
            val tags = setOf("smart", "cool")
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    labels = labels,
                    tags = tags,
                )
            val requestWithoutLabels =
                StoreAssetRequest(
                    alt = "an image",
                    tags = tags,
                )
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")
            val response = storeAssetMultipartSource(client, image, request, path = "profile").second!!
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")

            fetchAssetMetadata(client, "profile", labels = labels.mapKeys { "label:${it.key}" })!!.apply {
                tags shouldContainExactly tags
                labels shouldContainExactly labels
                alt shouldBe request.alt
                variants shouldHaveSize 1
                `class` shouldBe AssetClass.IMAGE
                entryId shouldBe response.entryId
            }
        }

    @Test
    fun `can fetch asset with subset of labels`() =
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
            val requestWithoutLabels =
                StoreAssetRequest(
                    alt = "an image",
                    tags = tags,
                )
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")
            val response = storeAssetMultipartSource(client, image, request, path = "profile").second!!
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")

            fetchAssetMetadata(client, "profile", labels = mapOf("phone" to "iphone"))!!.apply {
                tags shouldContainExactly tags
                labels shouldContainExactly labels
                alt shouldBe request.alt
                variants shouldHaveSize 1
                `class` shouldBe AssetClass.IMAGE
                entryId shouldBe response.entryId
            }
        }

    @Test
    fun `fetching with label values that do not apply to assets returns nothing`() =
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
            val requestWithoutLabels =
                StoreAssetRequest(
                    alt = "an image",
                    tags = tags,
                )
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")
            storeAssetMultipartSource(client, image, request, path = "profile")
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")

            fetchAssetMetadata(client, "profile", labels = mapOf("phone" to "android"), expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `fetching with label keys that do not apply to assets returns nothing`() =
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
            val requestWithoutLabels =
                StoreAssetRequest(
                    alt = "an image",
                    tags = tags,
                )
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")
            storeAssetMultipartSource(client, image, request, path = "profile")
            storeAssetMultipartSource(client, image, requestWithoutLabels, path = "profile")

            fetchAssetMetadata(client, "profile", labels = mapOf("tablet" to "iphone"), expectedStatus = HttpStatusCode.NotFound)
        }
}
