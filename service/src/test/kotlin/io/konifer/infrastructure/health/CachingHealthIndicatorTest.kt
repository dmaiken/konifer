package io.konifer.infrastructure.health

import io.kotest.matchers.shouldBe
import io.ktor.util.logging.Logger
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CachingHealthIndicatorTest {
    @Test
    fun `starts unhealthy and caches a successful check`() =
        runTest {
            var checks = 0
            val indicator =
                CachingHealthIndicator(
                    name = "test",
                    scope = backgroundScope,
                    healthCheck = {
                        checks++
                        true
                    },
                )

            indicator.isHealthy() shouldBe false

            runCurrent()

            indicator.isHealthy() shouldBe true
            indicator.isHealthy() shouldBe true
            checks shouldBe 1
        }

    @Test
    fun `refreshes the cached result after the interval`() =
        runTest {
            var checkResult = true
            val indicator =
                CachingHealthIndicator(
                    name = "test",
                    scope = backgroundScope,
                    healthCheck = { checkResult },
                    refreshInterval = 5.seconds,
                )

            runCurrent()
            indicator.isHealthy() shouldBe true

            checkResult = false
            advanceTimeBy(5.seconds)
            runCurrent()

            indicator.isHealthy() shouldBe false
        }

    @Test
    fun `records exceptions as unhealthy and continues checking`() =
        runTest {
            var shouldFail = true
            val logger = mockk<Logger>(relaxed = true)
            val indicator =
                CachingHealthIndicator(
                    name = "test",
                    scope = backgroundScope,
                    healthCheck = {
                        if (shouldFail) error("unavailable")
                        true
                    },
                    refreshInterval = 5.seconds,
                    logger = logger,
                )

            runCurrent()
            indicator.isHealthy() shouldBe false
            verify(exactly = 1) {
                logger.warn("Health indicator 'test' is unhealthy", any<IllegalStateException>())
            }

            advanceTimeBy(5.seconds)
            runCurrent()
            verify(exactly = 1) {
                logger.warn("Health indicator 'test' is unhealthy", any<IllegalStateException>())
            }

            shouldFail = false
            advanceTimeBy(5.seconds)
            runCurrent()

            indicator.isHealthy() shouldBe true
            verify(exactly = 1) {
                logger.info("Health indicator 'test' recovered")
            }
        }

    @Test
    fun `records a timed out check as unhealthy`() =
        runTest {
            var checks = 0
            val logger = mockk<Logger>(relaxed = true)
            val indicator =
                CachingHealthIndicator(
                    name = "test",
                    scope = backgroundScope,
                    healthCheck = {
                        checks++
                        delay(2.seconds)
                        true
                    },
                    refreshInterval = 10.seconds,
                    checkTimeout = 1.seconds,
                    logger = logger,
                )

            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            indicator.isHealthy() shouldBe false
            checks shouldBe 1
            verify(exactly = 1) {
                logger.warn("Health indicator 'test' timed out after 1s")
            }
        }
}
