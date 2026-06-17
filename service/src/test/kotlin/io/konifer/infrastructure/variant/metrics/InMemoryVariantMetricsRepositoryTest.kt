package io.konifer.infrastructure.variant.metrics

import io.konifer.domain.variant.VariantId
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class InMemoryVariantMetricsRepositoryTest {
    private val drainSignal = mockk<VariantMetricsDrainSignal>(relaxed = true)
    private val repository = InMemoryVariantMetricsRepository(drainSignal = drainSignal)
    private val path = "/images/example"

    @Test
    fun `drainLastAccessed returns recorded metrics and clears the buffer`() =
        runTest {
            val variantId = VariantId()
            val accessedAt = Instant.now()

            repository.recordVariantAccess(
                variantId = variantId,
                path = path,
                accessedAt = accessedAt,
            )

            repository.drainLastAccessed() shouldContainExactly
                mapOf(
                    variantId to
                        VariantAccessedInformation(
                            accessedAt = accessedAt,
                            path = path,
                        ),
                )
            repository.drainLastAccessed().shouldBeEmpty()
        }

    @Test
    fun `recordVariantAccess keeps the latest accessedAt for a variant`() =
        runTest {
            val variantId = VariantId()
            val firstAccessedAt = Instant.now()
            val laterAccessedAt = Instant.now()

            repository.recordVariantAccess(
                variantId = variantId,
                path = path,
                accessedAt = firstAccessedAt,
            )
            repository.recordVariantAccess(
                variantId = variantId,
                path = path,
                accessedAt = laterAccessedAt,
            )
            repository.recordVariantAccess(
                variantId = variantId,
                path = path,
                accessedAt = firstAccessedAt,
            )

            repository.drainLastAccessed().getValue(variantId).accessedAt shouldBe laterAccessedAt
        }

    @Test
    fun `recordVariantAccess requests a drain when the buffer size exceeds maxEntries`() =
        runTest {
            val repository =
                InMemoryVariantMetricsRepository(
                    drainSignal = drainSignal,
                    maxEntries = 1,
                )

            repository.recordVariantAccess(
                variantId = VariantId(),
                path = "/images/first",
                accessedAt = Instant.now(),
            )
            repository.recordVariantAccess(
                variantId = VariantId(),
                path = "/images/second",
                accessedAt = Instant.now(),
            )

            verify(exactly = 1) {
                drainSignal.requestDrain()
            }
        }

    @Test
    fun `recordVariantAccess does not request a drain when the buffer size is below maxEntries`() =
        runTest {
            repository.recordVariantAccess(
                variantId = VariantId(),
                path = "/images/first",
                accessedAt = Instant.now(),
            )
            repository.recordVariantAccess(
                variantId = VariantId(),
                path = "/images/second",
                accessedAt = Instant.now(),
            )

            verify(exactly = 0) {
                drainSignal.requestDrain()
            }
        }
}
