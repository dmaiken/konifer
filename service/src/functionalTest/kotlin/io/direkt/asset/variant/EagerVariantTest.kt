package io.direkt.asset.variant

import io.direkt.config.testInMemory
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetMetadata
import io.direkt.util.storeAssetMultipartSource
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAtLeast
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
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
            path-configuration = [
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
            path-configuration = [
                {
                    path = "/**"
                    s3 {
                      bucket = default-bucket
                    }
                }
                {
                    path = "/users/**"
                    eager-variants = [small, medium]
                    s3 {
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
}
