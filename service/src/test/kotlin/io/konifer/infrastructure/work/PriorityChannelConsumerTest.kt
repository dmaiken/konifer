package io.konifer.infrastructure.work

import io.konifer.matchers.beApproximately
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.atomic.AtomicInteger

class PriorityChannelConsumerTest {
    @Test
    fun `processes work items from high priority channel when nothing in background channel`() =
        runTest {
            val workItems = 300
            val high = Channel<Int>()
            val low = Channel<Int>()
            val consumer = PriorityChannelConsumer(high, low, 80)

            val counter = AtomicInteger(0)
            val handle =
                launch {
                    var count = 0
                    while (count < workItems) {
                        val workItem = consumer.nextWorkItem()
                        counter.addAndGet(workItem)
                        count++
                    }
                }

            repeat(workItems) {
                high.send(1)
            }
            handle.join()
            counter.get() shouldBe workItems
        }

    @Test
    fun `processes work items from background priority channel when nothing in high priority channel`() =
        runTest {
            val workItems = 300
            val high = Channel<Int>()
            val low = Channel<Int>()
            val consumer = PriorityChannelConsumer(high, low, 80)

            val counter = AtomicInteger(0)
            val handle =
                launch {
                    var count = 0
                    while (count < workItems) {
                        val workItem = consumer.nextWorkItem()
                        counter.addAndGet(workItem)
                        count++
                    }
                }

            repeat(workItems) {
                low.send(1)
            }
            handle.join()
            counter.get() shouldBe workItems
        }

    @ParameterizedTest
    @ValueSource(ints = [99, 90, 50])
    fun `respects priority when scheduling work from both channels`(highPriority: Int) =
        runTest {
            val workItems = 20000
            val high = Channel<Int>(capacity = workItems * 4)
            val low = Channel<Int>(capacity = workItems * 4)
            val consumer = PriorityChannelConsumer(high, low, highPriority)

            repeat(workItems * 4) {
                high.send(1)
                low.send(-1)
            }

            val highPulled = AtomicInteger(0)
            val lowPulled = AtomicInteger(0)
            val processed = AtomicInteger(0)
            val handle =
                launch {
                    var count = 0
                    while (count < workItems) {
                        val workItem = consumer.nextWorkItem()
                        if (workItem < 0) {
                            lowPulled.incrementAndGet()
                        } else {
                            highPulled.incrementAndGet()
                        }
                        processed.addAndGet(1)
                        count++
                    }
                }

            high.close()
            low.close()
            handle.join()

            // Ensure margin of error of 5%
            (highPulled.get() / workItems.toDouble()) should beApproximately(highPriority * 0.01, epsilon = 0.05)
            (lowPulled.get() / workItems.toDouble()) should beApproximately((100 - highPriority) * 0.01, epsilon = 0.05)
        }

    @ParameterizedTest
    @ValueSource(ints = [99, 90, 50])
    fun `eventually processes all channels regardless of priority`(highPriority: Int) =
        runTest {
            val workItems = 20000
            val high = Channel<Int>(capacity = workItems)
            val low = Channel<Int>(capacity = workItems)
            val consumer = PriorityChannelConsumer(high, low, highPriority)

            repeat(workItems) {
                high.send(1)
                low.send(-1)
            }

            val highPulled = AtomicInteger(0)
            val lowPulled = AtomicInteger(0)
            val processed = AtomicInteger(0)
            // Switch dispatcher because runTest skips delay() calls
            launch {
                var count = 0
                while (count < workItems * 2) {
                    val workItem = consumer.nextWorkItem()
                    if (workItem < 0) {
                        lowPulled.incrementAndGet()
                    } else {
                        highPulled.incrementAndGet()
                    }
                    processed.addAndGet(1)
                    count++
                }
            }.join()

            lowPulled.get() shouldBe workItems
            highPulled.get() shouldBe workItems
        }
}
