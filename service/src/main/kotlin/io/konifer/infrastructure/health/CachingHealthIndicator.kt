package io.konifer.infrastructure.health

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CachingHealthIndicator(
    private val name: String,
    scope: CoroutineScope,
    private val healthCheck: suspend () -> Boolean,
    private val refreshInterval: Duration = 5.seconds,
    private val checkTimeout: Duration = 2.seconds,
    private val logger: Logger = KtorSimpleLogger(CachingHealthIndicator::class.qualifiedName!!),
) : HealthIndicator {
    private val cachedHealth = AtomicReference<Boolean?>(null)

    init {
        scope.launch {
            while (isActive) {
                updateCachedHealth(runHealthCheck())

                delay(refreshInterval)
            }
        }
    }

    override fun isHealthy(): Boolean = cachedHealth.get() ?: false

    private suspend fun runHealthCheck(): HealthCheckResult =
        try {
            withTimeoutOrNull(checkTimeout) {
                HealthCheckResult(healthy = healthCheck())
            } ?: HealthCheckResult.timedOut
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            HealthCheckResult(healthy = false, failure = exception)
        }

    private fun updateCachedHealth(result: HealthCheckResult) {
        val previous = cachedHealth.getAndSet(result.healthy)

        when {
            !result.healthy && previous != false -> logFailure(result)
            result.healthy && previous == false -> logger.info("Health indicator '$name' recovered")
        }
    }

    private fun logFailure(result: HealthCheckResult) {
        when {
            result.failure != null -> logger.warn("Health indicator '$name' is unhealthy", result.failure)
            result.timedOut -> logger.warn("Health indicator '$name' timed out after $checkTimeout")
            else -> logger.warn("Health indicator '$name' is unhealthy")
        }
    }

    private data class HealthCheckResult(
        val healthy: Boolean,
        val failure: Throwable? = null,
        val timedOut: Boolean = false,
    ) {
        companion object Factory {
            val timedOut = HealthCheckResult(healthy = false, timedOut = true)
        }
    }
}
