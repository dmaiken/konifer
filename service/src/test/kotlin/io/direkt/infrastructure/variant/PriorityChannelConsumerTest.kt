package io.direkt.infrastructure.variant

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.matchers.beApproximately
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.atomic.AtomicInteger

class PriorityChannelConsumerTest {
    @Test
    fun `processes jobs from high priority channel when nothing in background channel`() =
        runTest {
            val jobs = 300
            val high = Channel<Int>()
            val low = Channel<Int>()
            val consumer = PriorityChannelConsumer(high, low, 80)

            val counter = AtomicInteger(0)
            val handle =
                launch {
                    var count = 0
                    while (count < jobs) {
                        val task = consumer.nextJob()
                        counter.addAndGet(task)
                        count++
                    }
                }

            repeat(jobs) {
                high.send(1)
            }
            handle.join()
            counter.get() shouldBe jobs
        }

    @Test
    fun `processes jobs from background priority channel when nothing in high priority channel`() =
        runTest {
            val jobs = 300
            val high = Channel<Int>()
            val low = Channel<Int>()
            val consumer = PriorityChannelConsumer(high, low, 80)

            val counter = AtomicInteger(0)
            val handle =
                launch {
                    var count = 0
                    while (count < jobs) {
                        val task = consumer.nextJob()
                        counter.addAndGet(task)
                        count++
                    }
                }

            repeat(jobs) {
                low.send(1)
            }
            handle.join()
            counter.get() shouldBe jobs
        }

    @ParameterizedTest
    @ValueSource(ints = [99, 90, 50])
    fun `respects priority when scheduling work from both channels`(highPriority: Int) =
        runTest {
            val jobs = 20000
            val high = Channel<Int>(capacity = jobs * 4)
            val low = Channel<Int>(capacity = jobs * 4)
            val consumer = PriorityChannelConsumer(high, low, highPriority)

            repeat(jobs * 4) {
                high.send(1)
                low.send(-1)
            }

            val highPulled = AtomicInteger(0)
            val lowPulled = AtomicInteger(0)
            val processed = AtomicInteger(0)
            val handle =
                launch {
                    var count = 0
                    while (count < jobs) {
                        val job = consumer.nextJob()
                        if (job < 0) {
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
            (highPulled.get() / jobs.toDouble()) should beApproximately(highPriority * 0.01, epsilon = 0.05)
            (lowPulled.get() / jobs.toDouble()) should beApproximately((100 - highPriority) * 0.01, epsilon = 0.05)
        }

    @ParameterizedTest
    @ValueSource(ints = [99, 90, 50])
    fun `eventually processes all channels regardless of priority`(highPriority: Int) =
        runTest {
            val jobs = 20000
            val high = Channel<Int>(capacity = jobs)
            val low = Channel<Int>(capacity = jobs)
            val consumer = PriorityChannelConsumer(high, low, highPriority)

            repeat(jobs) {
                high.send(1)
                low.send(-1)
            }

            val highPulled = AtomicInteger(0)
            val lowPulled = AtomicInteger(0)
            val processed = AtomicInteger(0)
            // Switch dispatcher because runTest skips delay() calls
            launch {
                var count = 0
                while (count < jobs * 2) {
                    val job = consumer.nextJob()
                    if (job < 0) {
                        lowPulled.incrementAndGet()
                    } else {
                        highPulled.incrementAndGet()
                    }
                    processed.addAndGet(1)
                    count++
                }
            }.join()

            lowPulled.get() shouldBe jobs
            highPulled.get() shouldBe jobs
        }
}
