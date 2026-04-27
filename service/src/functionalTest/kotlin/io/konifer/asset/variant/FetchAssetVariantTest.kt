package io.konifer.asset.variant

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.config.testInMemory
import io.konifer.matchers.shouldBeWithinOneOf
import io.konifer.util.createJsonClient
import io.konifer.util.fetchAssetContent
import io.konifer.util.fetchAssetMetadata
import io.konifer.util.storeAssetMultipartSource
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotStartWith
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class FetchAssetVariantTest {
    @Test
    fun `requested asset variants are persisted in configured bucket`() =
        testInMemory(
            """
            paths = [
                {
                    path = "/**"
                    object-store {
                      bucket = default-bucket
                    }
                }
                {
                    path = "/users/**"
                    object-store {
                      bucket = correct-bucket
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "users/123")

            // "create" the variant by requesting it
            fetchAssetContent(client, path = "users/123", expectedMimeType = "image/png", height = 100, width = 100)

            fetchAssetMetadata(client, path = "users/123")!!.apply {
                variants shouldHaveSize 2
                variants.forAll {
                    it.storeBucket shouldBe "correct-bucket"
                }
            }
        }

    @Test
    fun `fetched variant contains no thumbnail exif metadata`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request = StoreAssetRequest()
            storeAssetMultipartSource(client, image, request, path = "users/123")

            // "create" the variant by requesting it
            val variantContent =
                fetchAssetContent(
                    client = client,
                    path = "users/123",
                    expectedMimeType = "image/png",
                    height = 100,
                    width = 100,
                ).apply {
                    first.status shouldBe HttpStatusCode.OK
                    second shouldNotBe null
                }.second!!

            Vips.run { arena ->
                val image = VImage.newFromBytes(arena, variantContent)
                image.fields.forAll { it shouldNotStartWith "exif-ifd1" }
            }
        }

    /**
     * Test the ByteChannel buffers within Konifer
     */
    @Test
    fun `can fetch variant of large image`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/large/joshua-tree.jpeg")!!.readBytes()
            val request = StoreAssetRequest()
            storeAssetMultipartSource(client, image, request, path = "users/123")

            val variantContent =
                fetchAssetContent(
                    client = client,
                    path = "users/123",
                    expectedMimeType = ImageFormat.PNG.mimeType,
                    blur = 10,
                    width = 3000,
                    format = ImageFormat.PNG.format,
                ).apply {
                    first.status shouldBe HttpStatusCode.OK
                    second shouldNotBe null
                }.second!!

            Vips.run { arena ->
                val image = VImage.newFromBytes(arena, variantContent)
                image.width shouldBeWithinOneOf 3000
            }
        }
}
