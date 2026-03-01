package io.konifer.asset.variant

import io.konifer.PHash
import io.konifer.config.testInMemory
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.infrastructure.vips.transformer.HAMMING_DISTANCE_IDENTICAL
import io.konifer.util.createJsonClient
import io.konifer.util.fetchAssetContent
import io.konifer.util.fetchAssetMetadata
import io.konifer.util.storeAssetMultipartSource
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAtLeast
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class EagerVariantTest {
    @Test
    fun `can store asset and eager variants are generated`() =
        testInMemory(
            """
            variant-profiles = [
                {
                    name = small
                    w = 15
                },
                {
                    name = medium
                    h = 15
                }
            ]
            paths = [
                {
                    path = "/users/**"
                    eager-variants = [small, medium]
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = ImageIO.read(ByteArrayInputStream(image))
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val storeResponse = storeAssetMultipartSource(client, image, request, path = "users/123").second

            // eager variants should not be in this list
            storeResponse!!.variants shouldHaveSize 1

            await().untilCallTo {
                runBlocking {
                    fetchAssetMetadata(client, "users/123")!!.variants.size
                }
            } matches { count -> count == 3 }

            val variants = fetchAssetMetadata(client, "users/123")!!.variants
            variants.forExactly(1) {
                it.attributes.height shouldBe 15
                it.attributes.width shouldNotBe 15
            }
            variants.forExactly(1) {
                it.attributes.height shouldNotBe 15
                it.attributes.width shouldBe 15
            }
            variants.forAtLeast(1) {
                it.attributes.height shouldBe bufferedImage.height
                it.attributes.width shouldBe bufferedImage.width
            }
        }

    @Test
    fun `eager variants are added to the configured bucket in path configuration`() =
        testInMemory(
            """
            variant-profiles = [
                {
                    name = small
                    w = 15
                },
                {
                    name = medium
                    h = 15
                }
            ]
            paths = [
                {
                    path = "/**"
                    object-store {
                      bucket = default-bucket
                    }
                }
                {
                    path = "/users/**"
                    eager-variants = [small, medium]
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
            val storeResponse = storeAssetMultipartSource(client, image, request, path = "users/123").second

            // eager variants should not be in this list
            storeResponse!!.variants shouldHaveSize 1

            await().untilCallTo {
                runBlocking {
                    fetchAssetMetadata(client, "users/123")!!.variants.size
                }
            } matches { count -> count == 3 }

            val variants = fetchAssetMetadata(client, "users/123")!!.variants
            variants.forAll {
                it.storeBucket shouldBe "correct-bucket"
            }
        }

    @Test
    fun `eager variants are generated from preprocessed content`() =
        testInMemory(
            """
            variant-profiles = [
              {
                name = small
                w = 50
              }
            ]
            paths = [
              {
                path = "/users/**"
                eager-variants = [small]
                preprocessing {
                  enabled = true
                  image {
                    r = 180
                  }
                }      
              }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request = StoreAssetRequest()
            val storeResponse = storeAssetMultipartSource(client, image, request, path = "users/123").second

            // eager variants should not be in this list
            storeResponse!!.variants shouldHaveSize 1
            storeResponse.variants.forAll {
                it.isOriginalVariant shouldBe true
            }

            await().untilCallTo {
                runBlocking {
                    fetchAssetMetadata(client, "users/123")!!.variants.size
                }
            } matches { count -> count == 2 }

            val actualContent = fetchAssetContent(client, path = "users/123", profile = "small").second!!

            // Store same asset without preprocessing and fetch r = 180 + small variant profile
            storeAssetMultipartSource(client, image, request, path = "apple/123").second shouldNotBe null
            val expectedContent = fetchAssetContent(client, path = "apple/123", rotate = "180", profile = "small").second!!

            PHash.hammingDistance(actualContent, expectedContent) shouldBeLessThanOrEqual
                HAMMING_DISTANCE_IDENTICAL
        }
}
