package io.konifer.domain.variant

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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

        exception.message shouldBe "Pixel count must be positive: $value"
    }

    @ParameterizedTest
    @CsvSource(
        "1, 1",
        "1P, 1",
        "1KP, 1000",
        "8MP, 8000000",
        "8.2944MP, 8294400",
        "1GP, 1000000000",
        "' 2.5 mp ', 2500000",
        "0.000001MP, 1",
    )
    fun `parses pixel counts`(
        input: String,
        expectedPixels: Long,
    ) {
        PixelCount.parse(input) shouldBe expectedPixels.toPixelCount()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "-1P", "MP", "1.2.3MP", "1MiP", "1XP"])
    fun `rejects invalid pixel count strings`(input: String) {
        shouldThrow<IllegalArgumentException> {
            PixelCount.parse(input)
        }
    }

    @Test
    fun `rejects pixel counts that do not resolve to a whole pixel`() {
        val input = "0.0000001MP"

        val exception =
            shouldThrow<IllegalArgumentException> {
                PixelCount.parse(input)
            }

        exception.message shouldBe "Pixel count must resolve to a whole number within Long range: $input"
    }

    @Test
    fun `rejects pixel counts that overflow a long`() {
        val input = "${Long.MAX_VALUE}GP"

        val exception =
            shouldThrow<IllegalArgumentException> {
                PixelCount.parse(input)
            }

        exception.message shouldBe "Pixel count must resolve to a whole number within Long range: $input"
    }

    @Test
    fun `compares using the number of pixels`() {
        (PixelCount.parse("8MP") < PixelCount.parse("8.5MP")) shouldBe true
        (PixelCount.parse("8MP") == 8_000_000L.toPixelCount()) shouldBe true
    }

    @Test
    fun `serializer uses an exact canonical pixel count`() {
        val count = PixelCount.parse("8.2944MP")

        Json.encodeToString(PixelCountSerializer, count) shouldBe "\"8294400P\""
        Json.decodeFromString(PixelCountSerializer, "\"8294400P\"") shouldBe count
    }

    @OptIn(ExperimentalSerializationApi::class)
    @ParameterizedTest
    @CsvSource(
        "12345, 12345",
        "8MP, 8000000",
        "8.2944MP, 8294400",
    )
    fun `pixel count deserializes from a scalar HOCON value`(
        input: String,
        expectedPixels: Long,
    ) {
        val limits =
            Hocon.decodeFromConfig<TransformationLimitProperties>(
                ConfigFactory.parseString("max-pixels = $input"),
            )

        limits.maxPixels shouldBe expectedPixels.toPixelCount()
    }
}
