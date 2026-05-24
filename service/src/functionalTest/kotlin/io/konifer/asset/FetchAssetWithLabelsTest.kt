package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.client.KoniferResponse
import io.konifer.client.QuerySelectors
import io.konifer.common.asset.AssetClass
import io.konifer.common.http.StoreAssetRequest
import io.konifer.testInMemory
import io.konifer.util.fetchAssetMetadata
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class FetchAssetWithLabelsTest : BaseFunctionalTest() {
    @Test
    fun `can fetch asset with labels`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
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
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class
            val response =
                konifer.storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                )
            response::class shouldBe KoniferResponse.Success::class
            val entryIdWithLabels = (response as KoniferResponse.Success).body.entryId

            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class

            val metadata =
                konifer.fetchAssetMetadata(
                    path = "profile",
                    labels = labels,
                )
            metadata::class shouldBe KoniferResponse.Success::class
            with((metadata as KoniferResponse.Success).body) {
                this.tags shouldContainExactly tags
                this.labels shouldContainExactly labels
                this.alt shouldBe request.alt
                this.variants shouldHaveSize 1
                this.`class` shouldBe AssetClass.IMAGE
                this.entryId shouldBe entryIdWithLabels
            }
        }

    @Test
    fun `can fetch asset with labels and entryId`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
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
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class
            val response =
                konifer.storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                )
            response::class shouldBe KoniferResponse.Success::class
            val entryIdWithLabels = (response as KoniferResponse.Success).body.entryId

            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class

            val metadata =
                konifer.fetchAssetMetadata(
                    path = "profile",
                    querySelectors = QuerySelectors.EntryId(entryIdWithLabels),
                    labels = labels,
                )
            metadata::class shouldBe KoniferResponse.Success::class
            with((metadata as KoniferResponse.Success).body) {
                this.tags shouldContainExactly tags
                this.labels shouldContainExactly labels
                this.alt shouldBe request.alt
                this.variants shouldHaveSize 1
                this.`class` shouldBe AssetClass.IMAGE
                this.entryId shouldBe entryIdWithLabels
            }

            // Verify wrong entryId with right labels returns NotFound
            val notFoundResponse =
                konifer.fetchAssetMetadata(
                    path = "profile",
                    querySelectors = QuerySelectors.EntryId(entryIdWithLabels + 1),
                    labels = labels,
                )
            notFoundResponse::class shouldBe KoniferResponse.HttpError::class
            (notFoundResponse as KoniferResponse.HttpError).httpStatusCode shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `can fetch asset with namespaced labels`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
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
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class
            val response =
                konifer.storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                )
            response::class shouldBe KoniferResponse.Success::class
            val entryIdWithLabels = (response as KoniferResponse.Success).body.entryId

            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class

            val metadata =
                konifer.fetchAssetMetadata(
                    path = "profile",
                    labels = labels.mapKeys { "label:${it.key}" },
                )
            metadata::class shouldBe KoniferResponse.Success::class
            with((metadata as KoniferResponse.Success).body) {
                this.tags shouldContainExactly tags
                this.labels shouldContainExactly labels
                this.alt shouldBe request.alt
                this.variants shouldHaveSize 1
                this.`class` shouldBe AssetClass.IMAGE
                this.entryId shouldBe entryIdWithLabels
            }
        }

    @Test
    fun `can fetch asset with namespaced labels overloading variant transformation parameters`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
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
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class
            val response =
                konifer.storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                )
            response::class shouldBe KoniferResponse.Success::class
            val entryIdWithLabels = (response as KoniferResponse.Success).body.entryId

            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class

            val metadata =
                konifer.fetchAssetMetadata(
                    path = "profile",
                    labels = labels,
                )
            metadata::class shouldBe KoniferResponse.Success::class
            with((metadata as KoniferResponse.Success).body) {
                this.tags shouldContainExactly tags
                this.labels shouldContainExactly labels
                this.alt shouldBe request.alt
                this.variants shouldHaveSize 1
                this.`class` shouldBe AssetClass.IMAGE
                this.entryId shouldBe entryIdWithLabels
            }
        }

    @Test
    fun `can fetch asset with subset of labels`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
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
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class
            val response =
                konifer.storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                )
            response::class shouldBe KoniferResponse.Success::class
            val entryIdWithLabels = (response as KoniferResponse.Success).body.entryId

            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class

            val metadata =
                konifer.fetchAssetMetadata(
                    path = "profile",
                    labels = mapOf("phone" to "iphone"),
                )
            metadata::class shouldBe KoniferResponse.Success::class
            with((metadata as KoniferResponse.Success).body) {
                this.tags shouldContainExactly tags
                this.labels shouldContainExactly labels
                this.alt shouldBe request.alt
                this.variants shouldHaveSize 1
                this.`class` shouldBe AssetClass.IMAGE
                this.entryId shouldBe entryIdWithLabels
            }
        }

    @Test
    fun `fetching with label values that do not apply to assets returns nothing`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
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
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = request,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class

            val metadata =
                konifer.fetchAssetMetadata(
                    path = "profile",
                    labels = mapOf("phone" to "android"),
                )
            metadata::class shouldBe KoniferResponse.HttpError::class
            (metadata as KoniferResponse.HttpError).httpStatusCode shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `fetching with label keys that do not apply to assets returns nothing`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
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
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = request,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class
            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class

            val metadata =
                konifer.fetchAssetMetadata(
                    path = "profile",
                    labels = mapOf("tablet" to "iphone"),
                )
            metadata::class shouldBe KoniferResponse.HttpError::class
            (metadata as KoniferResponse.HttpError).httpStatusCode shouldBe HttpStatusCode.NotFound
        }
}
