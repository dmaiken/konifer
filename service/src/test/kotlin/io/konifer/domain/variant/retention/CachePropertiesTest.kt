package io.konifer.domain.variant.retention

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.RetentionPropertyKeys.CachePropertyKeys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class CachePropertiesTest {
    @Test
    fun `max-variants must be positive integers`() {
        shouldThrow<IllegalArgumentException> {
            CacheProperties(maxVariants = 0)
        }.message shouldBe "'${CachePropertyKeys.MAX_VARIANTS}' must be greater than zero"
    }

    @Test
    fun `access-score-half-life must be positive`() {
        shouldThrow<IllegalArgumentException> {
            CacheProperties(accessScoreHalfLife = (-1).hours)
        }.message shouldBe "'${CachePropertyKeys.ACCESS_SCORE_HALF_LIFE}' must be greater than zero"
    }
}
