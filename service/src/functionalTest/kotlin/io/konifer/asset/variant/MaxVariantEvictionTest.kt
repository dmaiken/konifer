package io.konifer.asset.variant

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory.testImage
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.testInMemory
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Test

class MaxVariantEvictionTest : BaseFunctionalTest() {
    @Test
    fun `a variant is evicted if the limit is exceeded when generating a new one`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  retention {
                    cache {
                      max-variants = 3
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = testImage()
            konifer()
                .storeAsset(
                    path = "users/123",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).shouldBeSuccessful()
                .body

            repeat(3) { idx ->
                konifer().fetchAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            height = 100 + idx
                        },
                )
            }
            val infoBeforeEviction =
                konifer()
                    .fetchAssetInfo(
                        path = "users/123",
                    ).shouldBeSuccessful()

            infoBeforeEviction.body.variants shouldHaveSize 4 // including original variant

            konifer().fetchAssetContentBytes(
                path = "users/123",
                requestedTransformation =
                    requestedTransformation {
                        height = 200
                    },
            )

            val infoAfterEviction =
                konifer()
                    .fetchAssetInfo(
                        path = "users/123",
                    ).shouldBeSuccessful()

            infoAfterEviction.body.variants shouldHaveSize 4 // cache doesn't grow
        }
}
