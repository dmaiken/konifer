package io.konifer.infrastructure.objectstore.inmemory

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InMemoryObjectStoreHealthIndicatorTest {
    @Test
    fun `is healthy`() {
        InMemoryObjectStoreHealthIndicator().isHealthy() shouldBe true
    }
}
