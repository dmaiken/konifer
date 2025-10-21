package io.asset.variant.generation

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

class PriorityChannelScheduler<T>(
    private val highPriorityChannel: Channel<T>,
    private val backgroundChannel: Channel<T>,
    private val highPriorityWeight: Int,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        require(highPriorityWeight in 1..100) {
            "High priority weight must be between 0 and 100"
        }
        logger.info("Initiated Variant generation scheduler with synchronous priority of: $highPriorityWeight")
    }

    suspend fun scheduleSynchronousJob(job: T) {
        highPriorityChannel.send(job)
    }

    suspend fun scheduleBackgroundJob(job: T) {
        backgroundChannel.send(job)
    }

    suspend fun nextJob(): T {
        val random = Random.nextInt(0, 100)
        return select {
            if (random > 100 - highPriorityWeight) {
                // Pull from high channel
                highPriorityChannel.onReceiveCatching { result ->
                    if (result.isClosed) throw CancellationException("High priority channel closed.")
                    result.getOrThrow()
                }
                backgroundChannel.onReceiveCatching { result ->
                    if (result.isClosed) throw CancellationException("Low priority channel closed.")
                    result.getOrThrow()
                }
            } else {
                backgroundChannel.onReceiveCatching { result ->
                    if (result.isClosed) throw CancellationException("Low priority channel closed.")
                    result.getOrThrow()
                }
                // Pull from high channel
                highPriorityChannel.onReceiveCatching { result ->
                    if (result.isClosed) throw CancellationException("High priority channel closed.")
                    result.getOrThrow()
                }
            }
        }
    }
}
