package io.direkt.asset.variant

import io.direkt.config.testInMemory
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetContent
import io.direkt.util.fetchAssetMetadata
import io.direkt.util.storeAssetMultipartSource
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FetchAssetVariantTest {
    @Test
    fun `requested asset variants are persisted in configured bucket`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    s3 {
                      bucket = default-bucket
                    }
                }
                {
                    path = "/users/**"
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
            storeAssetMultipartSource(client, image, request, path = "users/123")

            // "create" the variant by requesting it
            fetchAssetContent(client, path = "users/123", expectedMimeType = "image/png", height = 100, width = 100)

            fetchAssetMetadata(client, path = "users/123")!!.apply {
                variants shouldHaveSize 2
                variants.forAll {
                    it.bucket shouldBe "correct-bucket"
                }
            }
        }
}
