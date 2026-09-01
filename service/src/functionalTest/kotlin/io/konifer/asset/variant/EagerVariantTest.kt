package io.konifer.asset.variant

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory.testImage
import io.konifer.PHash
import io.konifer.client.KoniferResponse
import io.konifer.client.fold
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.Rotate
import io.konifer.infrastructure.vips.transformer.HAMMING_DISTANCE_IDENTICAL
import io.konifer.testInMemory
import io.konifer.util.fetchAssetContent
import io.konifer.util.fetchAssetInfo
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAtLeast
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import kotlin.test.junit.JUnitAsserter.fail

class EagerVariantTest : BaseFunctionalTest() {
    @Test
    fun `can store asset and eager variants are generated`() =
        testInMemory(
            """
            variant-profiles {
              small {
                w = 15
              }
              medium {
                h = 15
              }
            }
            paths {
              "/users/**" {
                transform {
                  eager-variants = [small, medium]
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
                ).fold(
                    onSuccess = { _ ->
                        await().untilCallTo {
                            runBlocking {
                                val response =
                                    konifer().fetchAssetInfo(
                                        path = "users/123",
                                    )
                                (response as KoniferResponse.Success).body.variants.size
                            }
                        } matches { count -> count == 3 }

                        val response =
                            konifer().fetchAssetInfo(
                                path = "users/123",
                            )
                        val variants = (response as KoniferResponse.Success).body.variants
                        variants.forExactly(1) {
                            it.attributes.height shouldBe 15
                            it.attributes.width shouldNotBe 15
                        }
                        variants.forExactly(1) {
                            it.attributes.height shouldNotBe 15
                            it.attributes.width shouldBe 15
                        }
                        variants.forAtLeast(1) {
                            it.attributes.height shouldBe attributes.height
                            it.attributes.width shouldBe attributes.width
                        }
                    },
                    onError = { _, _, _ -> fail("Request failed") },
                )
        }

    @Test
    fun `eager variants are added to the configured bucket in path configuration`() =
        testInMemory(
            """
            variant-profiles {
              small {
                w = 15
              }
              medium {
                h = 15
              }
            }
            paths {
              "/**" {
                object-store {
                  bucket = default-bucket
                }
              }
              "/users/**" {
                transform {
                  eager-variants = [small, medium]
                }
                object-store {
                  bucket = correct-bucket
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
                ).fold(
                    onSuccess = { storeResponse ->
                        // eager variants should not be in this list
                        storeResponse.variants shouldHaveSize 1

                        await().untilCallTo {
                            runBlocking {
                                fetchAssetInfo(client, "users/123")!!.variants.size
                            }
                        } matches { count -> count == 3 }

                        val variants = fetchAssetInfo(client, "users/123")!!.variants
                        variants.forAll {
                            it.storeBucket shouldBe "correct-bucket"
                        }
                    },
                    onError = { _, _, _ -> fail("Request failed") },
                )
        }

    @Test
    fun `eager variants are generated from preprocessed content`() =
        testInMemory(
            """
            variant-profiles {
              small {
                w = 50
              }
            }
            paths {
              "/users/**" {
                transform {
                  eager-variants = [small]
                  preprocessing {
                    enabled = true
                    r = 180
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
                ).fold(
                    onSuccess = { storeResponse ->
                        // eager variants should not be in this list
                        storeResponse.variants shouldHaveSize 1
                        storeResponse.variants.forAll {
                            it.isOriginalVariant shouldBe true
                        }

                        await().untilCallTo {
                            runBlocking {
                                fetchAssetInfo(client, "users/123")!!.variants.size
                            }
                        } matches { count -> count == 2 }

                        val actualContent = fetchAssetContent(client, path = "users/123", profile = "small").second!!

                        // Store same asset without preprocessing and fetch r = 180 + small variant profile
                        konifer().storeAsset(
                            path = "apple/123",
                            format = attributes.format,
                            request = StoreAssetRequest(),
                            bytes = image,
                        )::class shouldBe KoniferResponse.Success::class
                        konifer()
                            .fetchAssetContentBytes(
                                path = "apple/123",
                                requestedTransformation =
                                    requestedTransformation {
                                        rotate = Rotate.ONE_HUNDRED_EIGHTY
                                        profile = "small"
                                    },
                            ).fold(
                                onSuccess = { response ->
                                    PHash.hammingDistance(actualContent, response) shouldBeLessThanOrEqual
                                        HAMMING_DISTANCE_IDENTICAL
                                },
                                onError = { _, _, _ -> fail("Request failed") },
                            )
                    },
                    onError = { _, _, _ -> fail("Request failed") },
                )
        }
}
