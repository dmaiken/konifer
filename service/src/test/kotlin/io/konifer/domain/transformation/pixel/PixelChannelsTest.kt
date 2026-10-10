package io.konifer.domain.transformation.pixel

import com.typesafe.config.ConfigFactory
import io.konifer.domain.transformation.PreProcessingProperties
import io.konifer.domain.transformation.RequestedTransformation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class PixelChannelsTest {
    companion object {
        @JvmStatic
        fun channelCases() =
            listOf("RGB", "BGR", "RGBA", "BGRA", "ARGB", "ABGR").flatMap { expected ->
                listOf(
                    expected,
                    expected.lowercase(),
                    expected
                        .mapIndexed { index, channel ->
                            if (index % 2 == 0) channel.lowercaseChar() else channel
                        }.joinToString(""),
                ).map { input -> arguments(input, expected) }
            }
    }

    @ParameterizedTest
    @MethodSource("channelCases")
    fun `construction normalizes channel case and preserves order`(
        input: String,
        expected: String,
    ) {
        PixelChannels(input).value shouldBe expected
        input.toPixelChannels().value shouldBe expected
    }

    @Test
    fun `default channels are RGB`() {
        PixelChannels.default.value shouldBe "RGB"
    }

    @Test
    fun `equivalent channel cases have equal values and hash codes`() {
        val uppercase = PixelChannels("BGRA")
        val mixedCase = PixelChannels("bGrA")

        mixedCase shouldBe uppercase
        mixedCase.hashCode() shouldBe uppercase.hashCode()
    }

    @ParameterizedTest
    @ValueSource(strings = ["RRR", "AAAA"])
    fun `allows repeated channels`(channels: String) {
        PixelChannels(channels).value shouldBe channels
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "R", "RG", "RGBAB", "RGBARG"])
    fun `rejects channel counts outside three or four`(channels: String) {
        shouldThrow<IllegalArgumentException> {
            PixelChannels(channels)
        }.message shouldBe "Pixel channels must be between 3 and 4 characters"
    }

    @ParameterizedTest
    @ValueSource(strings = ["RGX", "R1B", "RGB ", " RGB", "R-B"])
    fun `rejects unsupported channels`(channels: String) {
        shouldThrow<IllegalArgumentException> {
            channels.toPixelChannels()
        }
    }

    @ParameterizedTest
    @MethodSource("channelCases")
    fun `JSON deserialization normalizes channel case and preserves order`(
        input: String,
        expected: String,
    ) {
        Json.decodeFromString<PixelChannels>(Json.encodeToString(input)).value shouldBe expected
    }

    @ParameterizedTest
    @MethodSource("channelCases")
    fun `JSON serialization writes a canonical string and round trips`(
        input: String,
        expected: String,
    ) {
        val channels = PixelChannels(input)
        val encoded = Json.encodeToString(channels)

        encoded shouldBe "\"$expected\""
        Json.decodeFromString<PixelChannels>(encoded) shouldBe channels
    }

    @OptIn(ExperimentalSerializationApi::class)
    @ParameterizedTest
    @MethodSource("channelCases")
    fun `HOCON deserialization normalizes scalar channels in transformation properties`(
        input: String,
        expected: String,
    ) {
        val config = ConfigFactory.parseString("pixel-channels = $input")
        val requested = Hocon.decodeFromConfig<RequestedTransformation>(config)
        val preprocessing = Hocon.decodeFromConfig<PreProcessingProperties>(config)

        requested.pixelChannels?.value shouldBe expected
        preprocessing.pixelChannels?.value shouldBe expected
        preprocessing.requestedImageTransformation.pixelChannels shouldBe requested.pixelChannels
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "RG", "RGBAB", "RGX", "R1B", "RGB "])
    fun `JSON deserialization validates channel values`(channels: String) {
        shouldThrow<IllegalArgumentException> {
            Json.decodeFromString<PixelChannels>(Json.encodeToString(channels))
        }
    }
}
