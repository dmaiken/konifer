package io.konifer.infrastructure.variant.metrics

import kotlinx.coroutines.channels.Channel

interface VariantMetricsDrainSignal {
    fun requestDrain()

    suspend fun awaitDrainRequest()
}

class ChannelVariantMetricsDrainSignal(
    /**
     * [Channel.CONFLATED] is here to compact the signal. If there are multiple signals to this channel,
     * I want them compacted into just one signal. We don't need 5 drains in a row.
     */
    private val channel: Channel<Unit> = Channel(Channel.CONFLATED),
) : VariantMetricsDrainSignal {
    override fun requestDrain() {
        channel.trySend(Unit)
    }

    override suspend fun awaitDrainRequest() {
        channel.receive()
    }
}
