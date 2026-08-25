package io.konifer.asset.limit

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory.testImage
import io.konifer.common.http.StoreAssetRequest
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PreProcessingLimitTest : BaseFunctionalTest() {
    @Test
    fun `preprocessing cannot exceed path transformation limits`() =
        testInMemory(
            """
            paths {
              "/users/**" {
                transform {
                  preprocessing {
                    enabled = true
                    image {
                      h = 100
                    }
                  }
                  limits {
                    max-width = 14
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = testImage()
            val error =
                konifer()
                    .storeAsset(
                        path = "users/123",
                        format = attributes.format,
                        request = StoreAssetRequest(),
                        bytes = image,
                    ) shouldHaveHttpError 400

            error.message shouldBe "Invalid transformation"
        }
}
