package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.client.EntryId
import io.konifer.client.KoniferResponse
import io.konifer.common.asset.AssetClass
import io.konifer.common.http.StoreAssetRequest
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
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
            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()
            val response =
                konifer
                    .storeAsset(
                        path = "profile",
                        format = attributes.format,
                        request = request,
                        bytes = image,
                    ).shouldBeSuccessful()
                    .body
            val entryIdWithLabels = response.entryId

            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = requestWithoutLabels,
                bytes = image,
            )::class shouldBe KoniferResponse.Success::class

            val metadata =
                konifer
                    .fetchAssetMetadata(
                        path = "profile",
                        querySelectors = EntryId(entryIdWithLabels),
                        labels = labels,
                    ).shouldBeSuccessful()
                    .body
            with(metadata) {
                this.tags shouldContainExactly tags
                this.labels shouldContainExactly labels
                this.alt shouldBe request.alt
                this.variants shouldHaveSize 1
                this.`class` shouldBe AssetClass.IMAGE
                this.entryId shouldBe entryIdWithLabels
            }

            // Verify wrong entryId with right labels returns NotFound
            konifer.fetchAssetMetadata(
                path = "profile",
                querySelectors = EntryId(entryIdWithLabels + 1),
                labels = labels,
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
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
            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()
            val response =
                konifer
                    .storeAsset(
                        path = "profile",
                        format = attributes.format,
                        request = request,
                        bytes = image,
                    ).shouldBeSuccessful()
                    .body
            val entryIdWithLabels = response.entryId

            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()

            val metadata =
                konifer
                    .fetchAssetMetadata(
                        path = "profile",
                        labels = labels.mapKeys { "label:${it.key}" },
                    ).shouldBeSuccessful()
                    .body
            with(metadata) {
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
            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()
            val response =
                konifer
                    .storeAsset(
                        path = "profile",
                        format = attributes.format,
                        request = request,
                        bytes = image,
                    ).shouldBeSuccessful()
                    .body
            val entryIdWithLabels = response.entryId

            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()

            val metadata =
                konifer
                    .fetchAssetMetadata(
                        path = "profile",
                        labels = labels,
                    ).shouldBeSuccessful()
                    .body
            with(metadata) {
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
            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()
            val response =
                konifer
                    .storeAsset(
                        path = "profile",
                        format = attributes.format,
                        request = request,
                        bytes = image,
                    ).shouldBeSuccessful()
                    .body
            val entryIdWithLabels = response.entryId

            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()

            val metadata =
                konifer
                    .fetchAssetMetadata(
                        path = "profile",
                        labels = mapOf("phone" to "iphone"),
                    ).shouldBeSuccessful()
                    .body
            with(metadata) {
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
            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()
            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                ).shouldBeSuccessful()
            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()

            konifer.fetchAssetMetadata(
                path = "profile",
                labels = mapOf("phone" to "android"),
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
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
            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()
            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = request,
                    bytes = image,
                ).shouldBeSuccessful()
            konifer
                .storeAsset(
                    path = "profile",
                    format = attributes.format,
                    request = requestWithoutLabels,
                    bytes = image,
                ).shouldBeSuccessful()

            konifer.fetchAssetMetadata(
                path = "profile",
                labels = mapOf("tablet" to "iphone"),
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
        }
}
