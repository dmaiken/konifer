package io.konifer.infrastructure.health

interface HealthIndicator {
    fun isHealthy(): Boolean
}
