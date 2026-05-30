package io.konifer.infrastructure

import io.konifer.common.image.ImageFormat
import io.konifer.domain.asset.AssetId
import io.konifer.domain.event.AssetReadyEvent
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Variant
import io.konifer.infrastructure.event.InMemoryEventBus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class InMemoryEventBusTest {
    private val bus = InMemoryEventBus()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when event is published it can be consumed`() =
        runTest {
            val counter = AtomicInteger(0)

            // Pass UnconfinedTestDispatcher to launch eagerly
            val consumer =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    bus.events.collect {
                        counter.incrementAndGet()
                    }
                }

            bus.publish(
                AssetReadyEvent(
                    pathConfiguration = PathConfiguration.default,
                    originalVariantFile = null,
                    originalVariant =
                        Variant.Pending.originalVariant(
                            assetId = AssetId(UUID.randomUUID()),
                            attributes = Attributes(width = 100, height = 100, format = ImageFormat.JPEG, colorSpace = ColorSpace.SRGB),
                            objectStoreBucket = "bucket",
                            objectStoreKey = "key",
                            lqip = LQIPs.NONE,
                        ),
                ),
            )

            counter.get() shouldBe 1
            consumer.cancel()
        }
}
