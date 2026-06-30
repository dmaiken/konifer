package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.TestImageType
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.testInMemory
import org.junit.jupiter.api.Test

class AssetUploadRulesTest : BaseFunctionalTest() {

    @Test
    fun `can upload asset that passes rules`() =
        testInMemory(
            """
                rule-definitions {
                  moon {
                    prompt = "This is a photo of a moon"
                    threshold = 0.7
                  }
                }
                
                paths {
                  "/**" {
                    upload-ruleset {
                      default = reject
                      accept-rules = [ 
                        { rule = moon } 
                      ]
                    }
                  }
                }
            """.trimIndent()
        ) {
            val (image, attributes) = ImageFactory.testImage(type = TestImageType.MOON, format = ImageFormat.PNG)
            val firstAsset =
                konifer()
                    .storeAsset(
                        path = "user/123",
                        bytes = image,
                        format = attributes.format,
                        request = StoreAssetRequest(),
                    ).shouldBeSuccessful()
                    .body
        }
}
