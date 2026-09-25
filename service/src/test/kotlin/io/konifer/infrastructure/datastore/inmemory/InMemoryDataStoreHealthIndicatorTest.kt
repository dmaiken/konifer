package io.konifer.infrastructure.datastore.inmemory

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InMemoryDataStoreHealthIndicatorTest {
    @Test
    fun `is healthy`() {
        InMemoryDataStoreHealthIndicator().isHealthy() shouldBe true
    }
}
