package io.konifer.infrastructure.datastore.postgres

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.SelectSelectStep
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

@OptIn(ExperimentalCoroutinesApi::class)
class PostgresHealthIndicatorTest {
    private val dslContext = mockk<DSLContext>()
    private val statement = mockk<SelectSelectStep<Record1<Int>>>()

    @Test
    fun `is healthy when select one succeeds`() =
        runTest {
            val record =
                mockk<Record1<Int>> {
                    every { value1() } returns 1
                }
            stubStatement(publisherOf(record))
            val indicator = PostgresHealthIndicator(backgroundScope, dslContext)

            runCurrent()

            indicator.isHealthy() shouldBe true
        }

    @Test
    fun `is unhealthy when select one fails`() =
        runTest {
            stubStatement(failingPublisher(IllegalStateException("unavailable")))
            val indicator = PostgresHealthIndicator(backgroundScope, dslContext)

            runCurrent()

            indicator.isHealthy() shouldBe false
        }

    private fun stubStatement(publisher: Publisher<Record1<Int>>) {
        every { dslContext.selectOne() } returns statement
        every { statement.subscribe(any<Subscriber<in Record1<Int>>>()) } answers {
            publisher.subscribe(firstArg<Subscriber<in Record1<Int>>>())
        }
    }

    private fun <T> publisherOf(value: T): Publisher<T> =
        publisher { subscriber ->
            subscriber.onNext(value)
            subscriber.onComplete()
        }

    private fun <T> failingPublisher(cause: Throwable): Publisher<T> =
        publisher { subscriber ->
            subscriber.onError(cause)
        }

    private fun <T> publisher(complete: (Subscriber<in T>) -> Unit): Publisher<T> =
        Publisher { subscriber ->
            subscriber.onSubscribe(
                object : Subscription {
                    private var completed = false

                    override fun request(elements: Long) {
                        if (completed) return
                        completed = true
                        complete(subscriber)
                    }

                    override fun cancel() {
                        completed = true
                    }
                },
            )
        }
}
