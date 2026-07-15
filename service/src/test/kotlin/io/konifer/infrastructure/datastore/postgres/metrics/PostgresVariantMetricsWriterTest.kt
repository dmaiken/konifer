package io.konifer.infrastructure.datastore.postgres.metrics

import com.typesafe.config.ConfigFactory
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantId
import io.konifer.infrastructure.datastore.createPendingAsset
import io.konifer.infrastructure.datastore.createPendingVariant
import io.konifer.infrastructure.datastore.postgres.PostgresContainerizedTest
import io.konifer.infrastructure.path.TriePathConfigurationRepository
import io.konifer.infrastructure.variant.metrics.ChannelVariantMetricsDrainSignal
import io.konifer.infrastructure.variant.metrics.InMemoryVariantMetricsRepository
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import konifer.jooq.tables.references.ASSET_VARIANT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit.SECONDS

class PostgresVariantMetricsWriterTest : PostgresContainerizedTest() {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val signal = ChannelVariantMetricsDrainSignal()
    private val metricsRepository = InMemoryVariantMetricsRepository(drainSignal = signal)
    private val pathConfigurationRepository =
        TriePathConfigurationRepository(
            ConfigFactory.parseString(
                """
                paths {
                  "/ttl/**" {
                    transform {
                      expire {
                        strategy = ttl
                        ttl = 1h
                      }
                    }
                  }
                  "/idle/**" {
                    transform {
                      expire {
                        strategy = idle
                        ttl = 1h
                      }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

    @AfterEach
    fun cancelScope() {
        scope.cancel()
    }

    @Test
    fun `flush updates last_accessed_at and leaves expires_at unchanged for non idle variants`() =
        runTest {
            PostgresVariantMetricsWriter(
                scope = scope,
                dslContext = dslContext,
                drainSignal = signal,
                variantMetricsRepository = metricsRepository,
                pathConfigurationRepository = pathConfigurationRepository,
            )

            val expiresAt = LocalDateTime.now(UTC).plusDays(2).truncatedTo(ChronoUnit.MILLIS)
            val variant = createReadyVariant(path = "/ttl/asset", expiresAt = expiresAt, transformationWidth = 300)
            val initiallyPersistedVariant = fetchVariant(variant.id)
            val accessedAt = Instant.now()

            metricsRepository.recordVariantAccess(
                variantId = variant.id,
                path = "/ttl/asset",
                accessedAt = accessedAt,
            )
            signal.requestDrain()

            await().atMost(5, SECONDS).untilAsserted {
                runBlocking {
                    val persistedVariant = fetchVariant(variant.id)

                    persistedVariant.lastAccessedAt?.truncatedTo(ChronoUnit.MILLIS) shouldBe
                        LocalDateTime.ofInstant(accessedAt, UTC).truncatedTo(ChronoUnit.MILLIS)
                    persistedVariant.expiresAt?.truncatedTo(ChronoUnit.MILLIS) shouldBe
                        initiallyPersistedVariant.expiresAt?.truncatedTo(ChronoUnit.MILLIS)
                }
            }
        }

    @Test
    fun `flush updates last_accessed_at and expires_at for idle variants`() =
        runTest {
            PostgresVariantMetricsWriter(
                scope = scope,
                dslContext = dslContext,
                drainSignal = signal,
                variantMetricsRepository = metricsRepository,
                pathConfigurationRepository = pathConfigurationRepository,
            )

            val variant =
                createReadyVariant(path = "/idle/asset", expiresAt = LocalDateTime.now(UTC).plusMinutes(5), transformationWidth = 400)
            val accessedAt = Instant.now()
            val accessedAtLocal = LocalDateTime.ofInstant(accessedAt, UTC).truncatedTo(ChronoUnit.MILLIS)

            metricsRepository.recordVariantAccess(
                variantId = variant.id,
                path = "/idle/asset",
                accessedAt = accessedAt,
            )
            signal.requestDrain()

            await().atMost(5, SECONDS).untilAsserted {
                runBlocking {
                    val persistedVariant = fetchVariant(variant.id)

                    persistedVariant.lastAccessedAt?.truncatedTo(ChronoUnit.MILLIS) shouldBe accessedAtLocal
                    persistedVariant.expiresAt?.truncatedTo(ChronoUnit.MILLIS) shouldBe accessedAtLocal.plusHours(1)
                }
            }
        }

    @Test
    fun `flush does not move last_accessed_at or expires_at backwards for stale idle accesses`() =
        runTest {
            PostgresVariantMetricsWriter(
                scope = scope,
                dslContext = dslContext,
                drainSignal = signal,
                variantMetricsRepository = metricsRepository,
                pathConfigurationRepository = pathConfigurationRepository,
            )

            val variant =
                createReadyVariant(path = "/idle/asset", expiresAt = LocalDateTime.now(UTC).plusMinutes(5), transformationWidth = 500)
            val currentLastAccessedAt = LocalDateTime.now(UTC).truncatedTo(ChronoUnit.MILLIS)
            val currentExpiresAt = currentLastAccessedAt.plusHours(1)
            val staleAccessedAt = currentLastAccessedAt.minusMinutes(15).toInstant(UTC)

            dslContext
                .update(ASSET_VARIANT)
                .set(ASSET_VARIANT.LAST_ACCESSED_AT, currentLastAccessedAt)
                .set(ASSET_VARIANT.EXPIRES_AT, currentExpiresAt)
                .where(ASSET_VARIANT.ID.eq(variant.id.value))
                .awaitFirstOrNull()

            metricsRepository.recordVariantAccess(
                variantId = variant.id,
                path = "/idle/asset",
                accessedAt = staleAccessedAt,
            )
            signal.requestDrain()

            await().atMost(5, SECONDS).untilAsserted {
                runBlocking {
                    val persistedVariant = fetchVariant(variant.id)

                    persistedVariant.lastAccessedAt?.truncatedTo(ChronoUnit.MILLIS) shouldBe currentLastAccessedAt
                    persistedVariant.expiresAt shouldBe currentExpiresAt
                }
            }
        }

    private suspend fun createReadyVariant(
        path: String,
        expiresAt: LocalDateTime?,
        transformationWidth: Int,
    ): Variant.Ready {
        val asset =
            assetRepository
                .storeNew(createPendingAsset(path = path))
                .markReady(LocalDateTime.now(UTC))
                .also { assetRepository.markReady(it) }

        val pendingVariant =
            createPendingVariant(
                assetId = asset.id,
                transformation =
                    Transformation(
                        width = transformationWidth,
                        height = 100,
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                    ),
                expiresAt = expiresAt,
            )

        return assetRepository
            .storeNewVariant(pendingVariant)
            .markReady(LocalDateTime.now(UTC))
            .also { assetRepository.markUploaded(it) }
    }

    private suspend fun fetchVariant(variantId: VariantId) =
        dslContext
            .selectFrom(ASSET_VARIANT)
            .where(ASSET_VARIANT.ID.eq(variantId.value))
            .awaitFirstOrNull()
            ?: fail("Variant ${variantId.value} not found")
}
