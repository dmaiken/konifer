package io.konifer.infrastructure.health

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class KoniferHealthIndicatorTest {
    @Test
    fun `is healthy when every indicator is healthy`() {
        val indicators =
            List(2) {
                mockk<HealthIndicator> {
                    every { isHealthy() } returns true
                }
            }

        KoniferHealthIndicator(indicators).isHealthy() shouldBe true
    }

    @Test
    fun `is unhealthy when any indicator is unhealthy`() {
        val healthy =
            mockk<HealthIndicator> {
                every { isHealthy() } returns true
            }
        val unhealthy =
            mockk<HealthIndicator> {
                every { isHealthy() } returns false
            }

        KoniferHealthIndicator(listOf(healthy, unhealthy)).isHealthy() shouldBe false
    }
}
