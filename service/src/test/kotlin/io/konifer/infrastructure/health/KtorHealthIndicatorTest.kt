package io.konifer.infrastructure.health

import io.kotest.matchers.shouldBe
import io.ktor.events.Events
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.mockk.mockk
import org.junit.jupiter.api.Test

class KtorHealthIndicatorTest {
    @Test
    fun `tracks the application lifecycle`() {
        val monitor = Events()
        val application = mockk<Application>()
        val indicator = KtorHealthIndicator(monitor)

        indicator.isHealthy() shouldBe false

        monitor.raise(ApplicationStarted, application)
        indicator.isHealthy() shouldBe true

        monitor.raise(ApplicationStopping, application)
        indicator.isHealthy() shouldBe false
    }
}
