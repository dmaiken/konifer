package io.konifer.domain.variant

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.ExpirePropertyKeys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import kotlin.time.Duration.Companion.minutes

class ExpirePropertiesTest {
    @Test
    fun `can create expire properties with ttl`() {
        val expire =
            ExpireProperties(
                mode = VariantExpirationMode.TTL,
                ttl = 10.minutes,
            )

        expire.mode shouldBe VariantExpirationMode.TTL
        expire.ttl shouldBe 10.minutes
    }

    @Test
    fun `can create expire properties with no expiry`() {
        val expire =
            ExpireProperties(
                mode = VariantExpirationMode.NEVER,
                ttl = null,
            )

        expire.mode shouldBe VariantExpirationMode.NEVER
        expire.ttl shouldBe null
    }

    @Test
    fun `cannot create expire properties with ttl mode and no ttl`() {
        shouldThrow<IllegalArgumentException> {
            ExpireProperties(
                mode = VariantExpirationMode.TTL,
                ttl = null,
            )
        }.message shouldBe "For variant expiry mode: ttl, ${ExpirePropertyKeys.TTL} property must be set"
    }

    @Test
    fun `expiresAt calculates expiry date`() {
        val fixedInstant = Instant.parse("2026-01-01T12:00:00Z")
        val fixedClock = Clock.fixed(fixedInstant, UTC)

        ExpireProperties(
            mode = VariantExpirationMode.TTL,
            ttl = 10.minutes,
        ).expiresAt(fixedClock) shouldBe LocalDateTime.now(fixedClock).plusMinutes(10)
    }

    @Test
    fun `expiresAt returns null if mode is never`() {
        ExpireProperties(
            mode = VariantExpirationMode.NEVER,
            ttl = null,
        ).expiresAt() shouldBe null
    }

    @Test
    fun `expiresAt returns null if mode is never even if ttl is populated`() {
        ExpireProperties(
            mode = VariantExpirationMode.NEVER,
            ttl = 10.minutes,
        ).expiresAt() shouldBe null
    }
}
