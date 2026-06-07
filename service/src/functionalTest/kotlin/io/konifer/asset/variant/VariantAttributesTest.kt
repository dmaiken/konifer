package io.konifer.asset.variant

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.BaseFunctionalTest
import io.konifer.common.http.StoreAssetRequest
import io.konifer.testInMemory
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class VariantAttributesTest : BaseFunctionalTest() {
    @Test
    fun `height and width are populated when image is stored`() =
        testInMemory {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request = StoreAssetRequest()
            val response = storeAssetMultipartSource(client, image, request, path = "profile").second!!
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)

                with(response.variants.first().attributes) {
                    height shouldBe source.height
                    width shouldBe source.width
                }
            }
        }

    @Test
    fun `height and width are populated when image is stored and preprocessed`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = true
                    image {
                      w = 200
                      h = 200
                      fit = stretch
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request = StoreAssetRequest()
            val response = storeAssetMultipartSource(client, image, request, path = "profile").second!!

            with(response.variants.first().attributes) {
                height shouldBe 200
                width shouldBe 200
            }
        }

    @Test
    fun `page count and loop is populated when image is stored`() =
        testInMemory {
            val image = javaClass.getResourceAsStream("/images/kermit/kermit.gif")!!.readBytes()
            val request = StoreAssetRequest()
            val response = storeAssetMultipartSource(client, image, request, path = "profile").second!!

            with(response.variants.first().attributes) {
                pageCount shouldNotBe null
                pageCount!! shouldBeGreaterThan 1
                loop shouldNotBe null
                loop!! shouldBe 0
            }
        }

    @Test
    fun `colorspace attribute is populated when image contains icc profile`() =
        testInMemory {
            val image = javaClass.getResourceAsStream("/images/metadata/iphone-p3.jpg")!!.readBytes()
            val request = StoreAssetRequest()
            val response = storeAssetMultipartSource(client, image, request, path = "profile").second!!

            response.variants
                .first()
                .attributes.colorSpace shouldBe "p3"
        }
}
