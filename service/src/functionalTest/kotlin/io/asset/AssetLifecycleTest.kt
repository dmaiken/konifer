package io.asset

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import io.asset.handler.AssetSource
import io.asset.model.AssetClass
import io.asset.model.StoreAssetRequest
import io.config.testInMemory
import io.image.model.ImageFormat
import io.image.vips.VipsOptionNames.OPTION_ACCESS
import io.image.vips.VipsOptionNames.OPTION_N
import io.image.vips.VipsOptionNames.OPTION_PAGE_HEIGHT
import io.kotest.matchers.collections.shouldBeOneOf
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AssetLifecycleTest {
    @ParameterizedTest
    @EnumSource(ImageFormat::class)
    fun `can create and get still image`(format: ImageFormat) =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.readBytes()
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
            storeAssetResponse.`class` shouldBe AssetClass.IMAGE
            storeAssetResponse.alt shouldBe "an image"
            storeAssetResponse.entryId shouldBe 0
            storeAssetResponse.labels shouldContainExactly labels
            storeAssetResponse.tags shouldContainExactly tags
            storeAssetResponse.source shouldBe AssetSource.UPLOAD
            storeAssetResponse.sourceUrl shouldBe null
            storeAssetResponse.variants shouldHaveSize 1
            Vips.run { arena ->
                val vImage = VImage.newFromBytes(arena, image)
                storeAssetResponse.variants.first().apply {
                    storeKey shouldEndWith format.extension
                    attributes.mimeType shouldBe format.mimeType
                    attributes.height shouldBe vImage.height
                    attributes.width shouldBe vImage.width
                    attributes.pageCount shouldBeOneOf listOf(null, 1)
                    attributes.loop shouldBeOneOf listOf(null, 0)
                }
            }
            fetchAssetMetadata(client, path = "profile") shouldBe storeAssetResponse
        }

    @Test
    fun `can create and get an image that is multi-paged`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/kermit.gif")!!.readBytes()
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
            storeAssetResponse.`class` shouldBe AssetClass.IMAGE
            storeAssetResponse.alt shouldBe "an image"
            storeAssetResponse.entryId shouldBe 0
            storeAssetResponse.labels shouldContainExactly labels
            storeAssetResponse.tags shouldContainExactly tags
            storeAssetResponse.source shouldBe AssetSource.UPLOAD
            storeAssetResponse.sourceUrl shouldBe null
            storeAssetResponse.variants shouldHaveSize 1
            Vips.run { arena ->
                // Load only one frame intentionally
                val vImage =
                    VImage.newFromBytes(
                        arena,
                        image,
                        VipsOption.Int(OPTION_N, -1),
                        VipsOption.Enum(OPTION_ACCESS, VipsAccess.ACCESS_SEQUENTIAL),
                    )
                storeAssetResponse.variants.first().apply {
                    storeKey shouldEndWith ".gif"
                    attributes.mimeType shouldBe "image/gif"
                    attributes.height shouldBe vImage.getInt(OPTION_PAGE_HEIGHT)
                    attributes.width shouldBe vImage.width
                    attributes.pageCount shouldBe 19
                    attributes.loop shouldBe 0
                }
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
