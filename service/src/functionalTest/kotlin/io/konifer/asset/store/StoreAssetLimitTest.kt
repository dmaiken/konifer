package io.konifer.asset.store

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.TestImageType
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.konifer.util.fetchAssetInfo
import io.konifer.util.storeAssetMultipartSource
import io.konifer.util.storeAssetUrlSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StoreAssetLimitTest : BaseFunctionalTest() {
    @ParameterizedTest
    @ValueSource(strings = ["100", "1KB"])
    fun `cannot store asset via url that is larger than configured max value`(maxBytes: String) =
        testInMemory(
            """
            source = {
              url = {
                allowed-domains = [ konifer.io ]
                max-bytes = $maxBytes
              }
            }
            """.trimIndent(),
        ) {
            // Come up with a better way to not rely on the internet
            val url = "https://konifer.io/img/konifer-small.png"
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    url = url,
                )

            storeAssetUrlSource(client, request, expectedStatus = HttpStatusCode.UnprocessableEntity)
            fetchAssetInfo(client, path = "profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @ParameterizedTest
    @ValueSource(strings = ["100", "1KB"])
    fun `cannot store asset via upload that is larger than configured max value`(maxBytes: String) =
        testInMemory(
            """
            source = {
              multipart = {
                max-bytes = $maxBytes
              }
            }
            """.trimIndent(),
        ) {
            val (image, _) = ImageFactory.testImage()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.PayloadTooLarge)

            fetchAssetInfo(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with height larger than max allowed height`() =
        testInMemory(
            """
            paths {
              "/**" {
                limits {
                  max-height = 10
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()

            val error =
                konifer().storeAsset(
                    path = "assets",
                    format = attributes.format,
                    bytes = image,
                    request = StoreAssetRequest(),
                ) shouldHaveHttpError 422

            error.message shouldBe "Content with dimensions (${attributes.height}, ${attributes.width}) exceeds maximum defined dimensions"
        }

    @Test
    fun `cannot store asset with width larger than max allowed width`() =
        testInMemory(
            """
            paths {
              "/**" {
                limits {
                  max-width = 10
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()

            val error =
                konifer().storeAsset(
                    path = "assets",
                    format = attributes.format,
                    bytes = image,
                    request = StoreAssetRequest(),
                ) shouldHaveHttpError 422

            error.message shouldBe "Content with dimensions (${attributes.height}, ${attributes.width}) exceeds maximum defined dimensions"
        }

    @ParameterizedTest
    @ValueSource(strings = ["200000", "0.2MP"])
    fun `cannot store asset with pixel count larger than max allowed pixel count`(maxPixelCount: String) =
        testInMemory(
            """
            paths {
              "/**" {
                limits {
                  max-pixels = $maxPixelCount
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()

            val error =
                konifer().storeAsset(
                    path = "assets",
                    format = attributes.format,
                    bytes = image,
                    request = StoreAssetRequest(),
                ) shouldHaveHttpError 422

            error.message shouldBe "Content with pixel count ${attributes.pixelCount} exceeds maximum pixel amount"
        }

    @ParameterizedTest
    @ValueSource(strings = ["100000", "0.1MP"])
    fun `cannot store animated asset with pixels per page larger than max configured`(maxPixelCount: String) =
        testInMemory(
            """
            paths {
              "/**" {
                limits {
                  max-pixels-per-page = $maxPixelCount
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) =
                ImageFactory.testImage(
                    format = ImageFormat.GIF,
                    type = TestImageType.KERMIT,
                )

            val error =
                konifer().storeAsset(
                    path = "assets",
                    format = attributes.format,
                    bytes = image,
                    request = StoreAssetRequest(),
                ) shouldHaveHttpError 422

            error.message shouldBe "Content with pixel count ${attributes.pixelCount} exceeds maximum pixel amount"
        }

    @Test
    fun `cannot store animated asset with page count larger than max configured`() =
        testInMemory(
            """
            paths {
              "/**" {
                limits {
                  max-pages = 2
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) =
                ImageFactory.testImage(
                    format = ImageFormat.GIF,
                    type = TestImageType.KERMIT,
                )

            val error =
                konifer().storeAsset(
                    path = "assets",
                    format = attributes.format,
                    bytes = image,
                    request = StoreAssetRequest(),
                ) shouldHaveHttpError 422

            error.message shouldStartWith "Animated content with page count" shouldContain "exceeds maximum defined page count"
        }

    @Test
    fun `single-paged animated asset ignores per-page settings`() =
        testInMemory(
            """
            paths {
              "/**" {
                limits {
                  max-pixels-per-page = 1000
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) =
                ImageFactory.testImage(
                    format = ImageFormat.GIF,
                    type = TestImageType.JOSHUA_TREE,
                )

            konifer()
                .storeAsset(
                    path = "assets",
                    format = attributes.format,
                    bytes = image,
                    request = StoreAssetRequest(),
                ).shouldBeSuccessful()
        }
}
