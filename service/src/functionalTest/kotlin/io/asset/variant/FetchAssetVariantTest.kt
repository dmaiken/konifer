package io.asset.variant

import io.asset.model.StoreAssetRequest
import io.config.testInMemory
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.util.createJsonClient
import io.util.fetchAssetContent
import io.util.fetchAssetInfo
import io.util.storeAsset
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
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request, path = "/users/123")

            // "create" the variant by requesting it
            fetchAssetContent(client, path = "/users/123", expectedMimeType = "image/png", height = 100, width = 100)

            fetchAssetInfo(client, path = "/users/123")!!.apply {
                variants shouldHaveSize 2
                variants.forAll {
                    it.bucket shouldBe "correct-bucket"
                }
            }
        }
}
