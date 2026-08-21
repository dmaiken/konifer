package io.konifer.image

import io.konifer.BaseFunctionalTest
import io.konifer.byteArrayToImage
import io.konifer.common.http.StoreAssetRequest
import io.konifer.domain.image.LQIPImplementation
import io.konifer.infrastructure.http.APP_LQIP_BLURHASH
import io.konifer.infrastructure.http.APP_LQIP_THUMBHASH
import io.konifer.testInMemory
import io.konifer.util.fetchAssetContent
import io.konifer.util.fetchAssetInfo
import io.konifer.util.fetchAssetLink
import io.konifer.util.storeAssetMultipartSource
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import org.junit.jupiter.api.Test

class ImagePreviewTest : BaseFunctionalTest() {
    companion object {
        const val PATH = "profile"
    }

    @Test
    fun `blurhash is generated and returned when storing an asset`() =
        testInMemory(
            """
            paths {
              "/**" {
                image {
                  lqip = [ "blurhash" ]
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf(LQIPImplementation.BLURHASH))
        }

    @Test
    fun `thumbhash is generated and returned when storing an asset`() =
        testInMemory(
            """
            paths {
              "/**" {
                image {
                  lqip = [ "thumbhash" ]
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf(LQIPImplementation.THUMBHASH))
        }

    @Test
    fun `blurhash and thumbhash are generated and returned when storing an asset`() =
        testInMemory(
            """
            paths {
              "/**" {
                image {
                  lqip = [ "thumbhash", "blurhash" ]
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf(LQIPImplementation.BLURHASH, LQIPImplementation.THUMBHASH))
        }

    @Test
    fun `no lqip is generated when storing an asset if none specified`() =
        testInMemory(
            """
            paths {
              "/**" {
                image {
                  lqip = [ ]
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf())
        }

    @Test
    fun `no lqip is generated when storing an asset if not enabled`() =
        testInMemory(
            """
            paths {
              "/**" {
                image { }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf())
        }

    @Test
    fun `requesting a variant gives back the same LQIPs if only resizing is done`() =
        testInMemory(
            """
            paths {
              "/**" {
                image {
                  lqip = [ "thumbhash", "blurhash" ]
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf(LQIPImplementation.BLURHASH, LQIPImplementation.THUMBHASH))

            // Generate the variant
            fetchAssetContent(
                client,
                width = bufferedImage.width - 10,
                height = bufferedImage.height - 10,
                expectCacheHit = false,
            )

            fetchAssetInfo(client, PATH)!!.apply {
                variants shouldHaveSize 2
                variants.forAll {
                    it.lqip.blurhash shouldNotBe null
                    it.lqip.thumbhash shouldNotBe null
                }
                // Assert only one unique lqip
                variants.map { it.lqip }.toSet() shouldHaveSize 1
            }
        }

    @Test
    fun `lqips are not regenerated when requesting variant with blur`() =
        testInMemory(
            """
            paths {
              "/**" {
                image {
                  lqip = [ "thumbhash", "blurhash" ]
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()

            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request)
            val result =
                fetchAssetLink(
                    client,
                    blur = 50,
                    expectCacheHit = false,
                )!!

            val original =
                fetchAssetLink(
                    client,
                    expectCacheHit = true,
                )!!

            result.lqip.blurhash shouldBe original.lqip.blurhash
            result.lqip.thumbhash shouldBe original.lqip.thumbhash
        }

    @Test
    fun `lqips are regenerated without corrupting the transformed variant`() =
        testInMemory(
            """
            paths {
              "/**" {
                image {
                  lqip = [ "thumbhash", "blurhash" ]
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val originalImage = byteArrayToImage(image)
            val originalLqip =
                storeAssetMultipartSource(client, image, StoreAssetRequest(), path = PATH)
                    .second!!
                    .variants
                    .single()
                    .lqip

            val (response, variantBytes) =
                fetchAssetContent(
                    client,
                    path = PATH,
                    filter = "sepia",
                    expectCacheHit = false,
                )

            val variantImage = byteArrayToImage(variantBytes!!)
            variantImage.width shouldBe originalImage.width
            variantImage.height shouldBe originalImage.height
            variantImage.getRGB(variantImage.width / 2, variantImage.height / 2) shouldNotBe
                originalImage.getRGB(originalImage.width / 2, originalImage.height / 2)

            response.headers[APP_LQIP_BLURHASH] shouldNotBe null
            response.headers[APP_LQIP_THUMBHASH] shouldNotBe null
            response.headers[APP_LQIP_BLURHASH] shouldNotBe originalLqip.blurhash
            response.headers[APP_LQIP_THUMBHASH] shouldNotBe originalLqip.thumbhash
        }

    @Test
    fun `lqips are generated without corrupting preprocessed content`() =
        testInMemory(
            """
            paths {
              "/**" {
                image {
                  lqip = [ "thumbhash", "blurhash" ]
                }
                transform {
                  preprocessing {
                    enabled = true
                    image {
                      filter = sepia
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val originalImage = byteArrayToImage(image)
            val stored = storeAssetMultipartSource(client, image, StoreAssetRequest(), path = PATH).second!!
            val storedLqip = stored.variants.single().lqip

            storedLqip.blurhash shouldNotBe null
            storedLqip.thumbhash shouldNotBe null

            val (response, preprocessedBytes) = fetchAssetContent(client, path = PATH, expectCacheHit = true)
            val preprocessedImage = byteArrayToImage(preprocessedBytes!!)
            preprocessedImage.width shouldBe originalImage.width
            preprocessedImage.height shouldBe originalImage.height
            preprocessedImage.getRGB(preprocessedImage.width / 2, preprocessedImage.height / 2) shouldNotBe
                originalImage.getRGB(originalImage.width / 2, originalImage.height / 2)
            response.headers[APP_LQIP_BLURHASH] shouldBe storedLqip.blurhash
            response.headers[APP_LQIP_THUMBHASH] shouldBe storedLqip.thumbhash
        }

    private suspend fun storeAndAssert(
        client: HttpClient,
        image: ByteArray,
        request: StoreAssetRequest,
        lqips: Set<LQIPImplementation>,
    ) {
        storeAssetMultipartSource(client, image, request, path = PATH).second!!.apply {
            variants.apply {
                size shouldBe 1
                if (lqips.contains(LQIPImplementation.BLURHASH)) {
                    first().lqip.blurhash shouldNotBe null
                } else {
                    first().lqip.blurhash shouldBe null
                }
                if (lqips.contains(LQIPImplementation.THUMBHASH)) {
                    first().lqip.thumbhash shouldNotBe null
                } else {
                    first().lqip.thumbhash shouldBe null
                }
            }
        }
        fetchAssetInfo(client, PATH)!!.apply {
            variants.apply {
                size shouldBe 1
                if (lqips.contains(LQIPImplementation.BLURHASH)) {
                    first().lqip.blurhash shouldNotBe null
                } else {
                    first().lqip.blurhash shouldBe null
                }
                if (lqips.contains(LQIPImplementation.THUMBHASH)) {
                    first().lqip.thumbhash shouldNotBe null
                } else {
                    first().lqip.thumbhash shouldBe null
                }
            }
        }
        fetchAssetContent(client, PATH)
    }
}
