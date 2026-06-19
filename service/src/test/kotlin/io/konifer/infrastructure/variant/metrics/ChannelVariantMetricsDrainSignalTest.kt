package io.konifer.infrastructure.variant.metrics

import io.kotest.matchers.nulls.shouldBeNull
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class ChannelVariantMetricsDrainSignalTest {
    @Test
    fun `awaitDrainRequest resumes after requestDrain`() =
        runTest {
            val signal = ChannelVariantMetricsDrainSignal()
            val awaitingDrain =
                async {
                    signal.awaitDrainRequest()
                }

            signal.requestDrain()

            awaitingDrain.await()
        }

    @Test
    fun `multiple drain requests are conflated until consumed`() =
        runTest {
            val signal = ChannelVariantMetricsDrainSignal()

            signal.requestDrain()
            signal.requestDrain()

            signal.awaitDrainRequest()

            withTimeoutOrNull(1.milliseconds) {
                signal.awaitDrainRequest()
            }.shouldBeNull()
        }
}
