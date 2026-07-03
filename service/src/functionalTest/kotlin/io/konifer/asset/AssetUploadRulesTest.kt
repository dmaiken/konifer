package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.TestImageType
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import org.junit.jupiter.api.Test

/**
 * Tests are all in one Test since model startup and embedding generation is expensive.
 */
class AssetUploadRulesTest : BaseFunctionalTest() {
    @Test
    fun `evaluates upload rules with real siglip2 models`() =
        testInMemory(
            """
            rule-definitions {
                joshua-tree {
                    prompts = [
                      "a joshua tree",
                      "a tree",
                      "joshua tree national park"
                    ]
                    threshold = 0.7
                }
            }
            paths {
              "/accept/**" {
                upload-ruleset {
                  default = reject
                  accept-rules = [ 
                    { rule = joshua-tree } 
                  ]
                }
              }
              "/reject/**" {
                upload-ruleset {
                  default = accept
                  reject-rules = [ 
                    { rule = joshua-tree } 
                  ]
                }
              }
            }
            """.trimIndent(),
        ) {
            ImageFormat.entries.forEach { format ->
                val (image, attributes) =
                    ImageFactory.testImage(
                        type = TestImageType.JOSHUA_TREE,
                        format = format,
                    )
                konifer()
                    .storeAsset(
                        path = "accept/${format.format}",
                        bytes = image,
                        format = attributes.format,
                        request = StoreAssetRequest(),
                    ).shouldBeSuccessful()
            }

            val (moonImage, moonAttributes) = ImageFactory.testImage(type = TestImageType.MOON, format = ImageFormat.PNG)
            konifer()
                .storeAsset(
                    path = "accept/moon",
                    bytes = moonImage,
                    format = moonAttributes.format,
                    request = StoreAssetRequest(),
                ).shouldHaveHttpError(400)

            val (joshuaTreeImage, joshuaTreeAttributes) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE)
            konifer()
                .storeAsset(
                    path = "reject/joshua-tree",
                    bytes = joshuaTreeImage,
                    format = joshuaTreeAttributes.format,
                    request = StoreAssetRequest(),
                ).shouldHaveHttpError(400)
        }
}
