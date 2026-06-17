package io.konifer.domain.variant

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.ExpirePropertyKeys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import kotlin.time.Duration.Companion.minutes

class ExpirationPropertiesTest {
    @ParameterizedTest
    @EnumSource(VariantExpirationStrategy::class, mode = EnumSource.Mode.EXCLUDE, names = ["NEVER"])
    fun `can create expire properties with ttl`(strategy: VariantExpirationStrategy) {
        val expire =
            ExpirationProperties(
                strategy = strategy,
                ttl = 10.minutes,
            )

        expire.strategy shouldBe strategy
        expire.ttl shouldBe 10.minutes
    }

    @Test
    fun `can create expire properties with no expiry`() {
        val expire =
            ExpirationProperties(
                strategy = VariantExpirationStrategy.NEVER,
                ttl = null,
            )

        expire.strategy shouldBe VariantExpirationStrategy.NEVER
        expire.ttl shouldBe null
    }

    @ParameterizedTest
    @EnumSource(VariantExpirationStrategy::class, mode = EnumSource.Mode.EXCLUDE, names = ["NEVER"])
    fun `cannot create expire properties with a ttl strategy and no ttl`(strategy: VariantExpirationStrategy) {
        shouldThrow<IllegalArgumentException> {
            ExpirationProperties(
                strategy = strategy,
                ttl = null,
            )
        }.message shouldBe
            "For variant expiry strategy: '${Json.encodeToString(strategy)}', '${ExpirePropertyKeys.TTL}' property must be set"
    }

    @Test
    fun `expiresAt calculates expiry date`() {
        val fixedInstant = Instant.parse("2026-01-01T12:00:00Z")
        val fixedClock = Clock.fixed(fixedInstant, UTC)

        ExpirationProperties(
            strategy = VariantExpirationStrategy.TTL,
            ttl = 10.minutes,
        ).expiresAt(fixedClock) shouldBe LocalDateTime.now(fixedClock).plusMinutes(10)
    }

    @Test
    fun `expiresAt returns null if strategy is never`() {
        ExpirationProperties(
            strategy = VariantExpirationStrategy.NEVER,
            ttl = null,
        ).expiresAt() shouldBe null
    }

    @Test
    fun `expiresAt returns null if strategy is never even if ttl is populated`() {
        ExpirationProperties(
            strategy = VariantExpirationStrategy.NEVER,
            ttl = 10.minutes,
        ).expiresAt() shouldBe null
    }
}
