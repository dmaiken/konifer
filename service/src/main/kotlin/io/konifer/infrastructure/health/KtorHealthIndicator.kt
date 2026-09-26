package io.konifer.infrastructure.health

import io.ktor.events.Events
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import java.util.concurrent.atomic.AtomicBoolean

class KtorHealthIndicator(
    monitor: Events,
) : HealthIndicator {
    private val isReady = AtomicBoolean(false)

    init {
        monitor.subscribe(ApplicationStarted) {
            isReady.set(true)
        }

        monitor.subscribe(ApplicationStopping) {
            isReady.set(false)
        }
    }

    override fun isHealthy(): Boolean = isReady.get()
}
