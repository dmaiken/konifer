package io.konifer.asset.variant

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory.testImage
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.testInMemory
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ExpiringVariantTest : BaseFunctionalTest() {
    @Test
    fun `on-variant can be expired using ttl`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  expire {
                    strategy = ttl
                    ttl = 500ms
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

            konifer().fetchAssetContentBytes(
                path = "users/123",
                requestedTransformation = requestedTransformation { height = 100 },
            )

            val info =
                konifer()
                    .fetchAssetInfo(
                        path = "users/123",
                    ).shouldBeSuccessful()
                    .body

            info.variants shouldHaveSize 2
            info.variants.forExactly(1) {
                it.transformation?.height shouldBe 100
            }

            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                runBlocking {
                    konifer()
                        .fetchAssetInfo(
                            path = "users/123",
                        ).shouldBeSuccessful()
                        .body.variants shouldHaveSize 1
                }
            }
        }

    @Test
    fun `eager variant can be expired using ttl`() =
        testInMemory(
            """
            variant-profiles {
              small {
                h = 100
              }
            }
            paths {
              "/**" {
                transform {
                  expire {
                    strategy = ttl
                    ttl = 500ms
                  }
                  eager-variants = [ small ]
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

            konifer().fetchAssetContentBytes(
                path = "users/123",
                requestedTransformation = requestedTransformation { profile = "small" },
            )

            val info =
                konifer()
                    .fetchAssetInfo(
                        path = "users/123",
                    ).shouldBeSuccessful()
                    .body

            info.variants shouldHaveSize 2
            info.variants.forExactly(1) {
                it.transformation?.height shouldBe 100
            }

            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                runBlocking {
                    konifer()
                        .fetchAssetInfo(
                            path = "users/123",
                        ).shouldBeSuccessful()
                        .body.variants shouldHaveSize 1
                }
            }
        }
}
