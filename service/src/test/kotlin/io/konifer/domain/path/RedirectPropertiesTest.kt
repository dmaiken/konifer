package io.konifer.domain.path

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedirectPropertiesTest {
    @Test
    fun `presigned url ttl cannot be negative if using presigned redirect mode`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                RedirectProperties(
                    strategy = RedirectStrategy.PRESIGNED,
                    preSigned =
                        PreSignedProperties(
                            ttl = (-1).minutes,
                        ),
                )
            }
        exception.message shouldBe "Presigned TTL must be positive"
    }

    @Test
    fun `presigned url ttl cannot be greater than 7 days if using presigned redirect mode`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                RedirectProperties(
                    strategy = RedirectStrategy.PRESIGNED,
                    preSigned =
                        PreSignedProperties(
                            ttl = 7.days.plus(1.seconds),
                        ),
                )
            }
        exception.message shouldBe "Presigned TTL cannot be greater than 7 days"
    }
}
