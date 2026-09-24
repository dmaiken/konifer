package io.konifer.domain.variant.retention

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.RetentionPropertyKeys.CachePropertyKeys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class CachePropertiesTest {
    @ParameterizedTest
    @ValueSource(ints = [Int.MIN_VALUE, -1, 0])
    fun `max-variants must be positive integers`(maxVariants: Int) {
        shouldThrow<IllegalArgumentException> {
            CacheProperties(maxVariants = maxVariants)
        }.message shouldBe "'${CachePropertyKeys.MAX_VARIANTS}' must be greater than zero"
    }

    @Test
    fun `access-score-half-life must not be negative`() {
        shouldThrow<IllegalArgumentException> {
            CacheProperties(accessScoreHalfLife = (-1).hours)
        }.message shouldBe "'${CachePropertyKeys.ACCESS_SCORE_HALF_LIFE}' must be greater than zero"
    }

    @Test
    fun `access-score-half-life must not be zero`() {
        shouldThrow<IllegalArgumentException> {
            CacheProperties(accessScoreHalfLife = Duration.ZERO)
        }.message shouldBe "'${CachePropertyKeys.ACCESS_SCORE_HALF_LIFE}' must be greater than zero"
    }

    @Test
    fun `minimum max-variants is one`() {
        CacheProperties(maxVariants = 1).maxVariants shouldBe 1
    }

    @Test
    fun `has cache default`() {
        CacheProperties() shouldBe
            CacheProperties(
                maxVariants = 16,
                accessScoreHalfLife = 1.hours,
            )
    }
}
