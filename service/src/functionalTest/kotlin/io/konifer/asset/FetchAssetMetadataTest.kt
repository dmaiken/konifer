package io.konifer.asset

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.common.asset.AssetClass
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.config.testInMemory
import io.konifer.util.fetchAllAssetMetadata
import io.konifer.util.fetchAssetLink
import io.konifer.util.fetchAssetMetadata
import io.konifer.util.storeAssetMultipartSource
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class FetchAssetMetadataTest {
    @Test
    fun `getting all asset info with path returns all info`() =
        testInMemory {
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
    fun `variant transformation data is returned in metadata`() =
        testInMemory {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request = StoreAssetRequest()
            storeAssetMultipartSource(client, image, request, path = "profile")

            fetchAssetLink(
                client = client,
                path = "profile",
                height = 50,
                width = 40,
                fit = "stretch",
                format = "jpg",
                gravity = "attention",
                rotate = "90",
                flip = "v",
                filter = "sepia",
                blur = 10,
                quality = 90,
                pad = 10,
                padColor = "#123456",
                strip = "xmp,exif",
                colorSpace = "p3",
                expectCacheHit = false,
            )

            fetchAssetMetadata(client, path = "profile")!!.apply {
                variants shouldHaveSize 2
                variants.forExactly(1) {
                    it.isOriginalVariant shouldBe true
                }
                variants.forExactly(1) {
                    with(it.transformation!!) {
                        height shouldBe 50
                        width shouldBe 40
                        fit shouldBe Fit.STRETCH
                        format shouldBe ImageFormat.JPEG.format
                        gravity shouldBe Gravity.ATTENTION
                        rotate shouldBe Rotate.TWO_HUNDRED_SEVENTY
                        flip shouldBe Flip.H
                        filter shouldBe Filter.SEPIA
                        blur shouldBe 10
                        quality shouldBe 90
                        padding.amount shouldBe 10
                        padding.color shouldBe listOf(18, 52, 86, 255)
                        metadata.strip shouldBe listOf(MetadataType.EXIF, MetadataType.XMP)
                        colorSpace shouldBe "p3"
                    }
                }
            }
        }

    @Test
    fun `fetching info of asset that does not exist returns not found`() =
        testInMemory {
            fetchAssetMetadata(client, UuidCreator.getRandomBasedFast().toString(), expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `fetching info of asset path that does not contain any assets returns not found`() =
        testInMemory {
            fetchAllAssetMetadata(client, UuidCreator.getRandomBasedFast().toString(), limit = 10) shouldHaveSize 0
        }
}
