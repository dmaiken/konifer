package io.konifer.infrastructure.objectstore.inmemory

import io.konifer.infrastructure.health.HealthIndicator

class InMemoryObjectStoreHealthIndicator : HealthIndicator {
    override fun isHealthy(): Boolean = true
}
