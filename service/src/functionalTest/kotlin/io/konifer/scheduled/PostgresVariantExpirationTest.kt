package io.konifer.scheduled

import io.konifer.BaseTestContainersTest
import io.konifer.ImageFactory.testImage
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.testPostgres
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.TimeUnit

/**
 * Verifies the Postgres job configuration and metric writing works
 */
class PostgresVariantExpirationTest : BaseTestContainersTest() {
    @ParameterizedTest
    @ValueSource(strings = ["ttl", "idle"])
    fun `on-demand variant expires in ttl mode`(strategy: String) =
        testPostgres(
            postgres,
            """
            postgres.scheduled-jobs.expiredVariantsSweeperInterval = 1s
            postgres.scheduled-jobs.variantMetricsFlushInterval = 1s
            postgres.scheduled-jobs.jobPollingInterval = 100ms
            paths {
              "/**" {
                transform {
                  expire {
                    strategy = $strategy
                    ttl = 1s
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
    fun `eager variant expires in ttl mode`() =
        testPostgres(
            postgres,
            """
            postgres.scheduled-jobs.expiredVariantsSweeperInterval = 1s
            postgres.scheduled-jobs.jobPollingInterval = 100ms
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
                    ttl = 1s
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

            // Await eager variant being created
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                runBlocking {
                    konifer()
                        .fetchAssetInfo(
                            path = "users/123",
                        ).shouldBeSuccessful()
                        .body.variants shouldHaveSize 2
                }
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
    fun `eager variant only expires when accessed in idle mode`() =
        testPostgres(
            postgres,
            """
            postgres.scheduled-jobs.expiredVariantsSweeperInterval = 1s
            postgres.scheduled-jobs.variantMetricsFlushInterval = 1s
            postgres.scheduled-jobs.jobPollingInterval = 100ms
            variant-profiles {
              small {
                h = 100
              }
            }
            paths {
              "/**" {
                transform {
                  expire {
                    strategy = idle
                    ttl = 1s
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

            // Await eager variant being created
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                runBlocking {
                    konifer()
                        .fetchAssetInfo(
                            path = "users/123",
                        ).shouldBeSuccessful()
                        .body.variants shouldHaveSize 2
                }
            }
            // Assert not expired yet
            Awaitility
                .await()
                .during(2, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted {
                    runBlocking {
                        konifer()
                            .fetchAssetInfo(
                                path = "users/123",
                            ).shouldBeSuccessful()
                            .body.variants shouldHaveSize 2
                    }
                }
            konifer().fetchAssetContentBytes(
                path = "users/123",
                requestedTransformation = requestedTransformation { profile = "small" },
            )

            // Now it should expire
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
