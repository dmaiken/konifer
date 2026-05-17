package io.konifer.asset.variant

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.ImageFactory.testImage
import io.konifer.client.KoniferResponse
import io.konifer.client.fold
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.config.testInMemory
import io.konifer.matchers.shouldBeFormat
import io.konifer.matchers.shouldBeWithinOneOf
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotStartWith
import org.junit.jupiter.api.Test
import kotlin.test.junit.JUnitAsserter.fail

class FetchAssetVariantTest {
    @Test
    fun `requested asset variants are persisted in configured bucket`() =
        testInMemory(
            """
            paths = [
                {
                    path = "/**"
                    object-store {
                      bucket = default-bucket
                    }
                }
                {
                    path = "/users/**"
                    object-store {
                      bucket = correct-bucket
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val (image, attributes) = testImage()
            konifer
                .storeAsset(
                    path = "users/123",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).fold(
                    onSuccess = { it },
                    onError = { _, _, _ -> fail("Request failed") },
                )

            val response =
                konifer.getAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            height = 100
                            width = 100
                        },
                ) as KoniferResponse.Success
            response.body shouldBeFormat ImageFormat.JPEG

            konifer
                .getAssetMetadata(
                    path = "users/123",
                ).fold(
                    onSuccess = { response ->
                        response.variants shouldHaveSize 2
                        response.variants.forAll {
                            it.storeBucket shouldBe "correct-bucket"
                        }
                    },
                    onError = { _, _, _ -> fail("Request failed") },
                )
        }

    @Test
    fun `fetched variant contains no thumbnail exif metadata`() =
        testInMemory {
            val (image, attributes) = testImage()
            konifer
                .storeAsset(
                    path = "users/123",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).fold(
                    onSuccess = { },
                    onError = { _, _, _ -> fail("Request failed") },
                )

            konifer
                .getAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            height = 100
                            width = 100
                        },
                ).fold(
                    onSuccess = { bytes ->
                        bytes shouldBeFormat ImageFormat.JPEG
                        Vips.run { arena ->
                            val image = VImage.newFromBytes(arena, bytes)
                            image.fields.forAll { it shouldNotStartWith "exif-ifd1" }
                        }
                    },
                    onError = { _, _, _ -> fail("Request failed") },
                )
        }

    /**
     * Test the ByteChannel buffers within Konifer
     */
    @Test
    fun `can fetch variant of large image`() =
        testInMemory {
            val (image, attributes) = testImage()
            konifer
                .storeAsset(
                    path = "users/123",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).fold(
                    onSuccess = { },
                    onError = { _, _, _ -> fail("Request failed") },
                )

            konifer
                .getAssetContentBytes(
                    path = "users/123",
                    requestedTransformation =
                        requestedTransformation {
                            blur = 10
                            width = 3000
                        },
                ).fold(
                    onSuccess = { bytes ->
                        bytes shouldBeFormat ImageFormat.JPEG
                        Vips.run { arena ->
                            val image = VImage.newFromBytes(arena, bytes)
                            image.width shouldBeWithinOneOf 3000
                        }
                    },
                    onError = { _, _, _ -> fail("Request failed") },
                )
        }
}
