package io.konifer.domain.variant

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PixelCountTest {
    @ParameterizedTest
    @ValueSource(longs = [1L, Long.MAX_VALUE])
    fun `pixel count accepts positive values`(value: Long) {
        value.toPixelCount().value shouldBe value
    }

    @ParameterizedTest
    @ValueSource(longs = [Long.MIN_VALUE, -1L, 0L])
    fun `pixel count rejects non-positive values`(value: Long) {
        val exception =
            shouldThrow<IllegalArgumentException> {
                value.toPixelCount()
            }

        exception.message shouldBe "Pixel count must be a positive number: $value"
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `pixel count deserializes from a scalar HOCON value`() {
        val limits =
            Hocon.decodeFromConfig<LimitProperties>(
                ConfigFactory.parseString("max-pixels = 12345"),
            )

        limits.maxPixels shouldBe 12_345L.toPixelCount()
    }
}
