package io.konifer.infrastructure.health

class KoniferHealthIndicator(
    private val indicators: List<HealthIndicator>,
) : HealthIndicator {
    override fun isHealthy(): Boolean = indicators.all { it.isHealthy() }
}
