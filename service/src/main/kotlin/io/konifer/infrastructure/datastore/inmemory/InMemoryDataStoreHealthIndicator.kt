package io.konifer.infrastructure.datastore.inmemory

import io.konifer.infrastructure.health.HealthIndicator

class InMemoryDataStoreHealthIndicator : HealthIndicator {
    override fun isHealthy(): Boolean = true
}
