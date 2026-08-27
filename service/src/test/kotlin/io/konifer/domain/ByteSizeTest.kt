package io.konifer.domain

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ByteSizeTest {
    @ParameterizedTest
    @CsvSource(
        "1, 1",
        "1B, 1",
        "1KB, 1000",
        "5mb, 5000000",
        "1GB, 1000000000",
        "1KiB, 1024",
        "1MiB, 1048576",
        "1GiB, 1073741824",
        "' 2 MiB ', 2097152",
    )
    fun `parses byte sizes`(
        input: String,
        expectedBytes: Long,
    ) {
        ByteSize.parse(input) shouldBe ByteSize(expectedBytes)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "-1B", "1.5MB", "1XB", "MB"])
    fun `rejects invalid byte size strings`(input: String) {
        shouldThrow<IllegalArgumentException> {
            ByteSize.parse(input)
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [Long.MIN_VALUE, -1L, 0L])
    fun `rejects non-positive byte counts`(bytes: Long) {
        val exception =
            shouldThrow<IllegalArgumentException> {
                ByteSize(bytes)
            }

        exception.message shouldBe "Byte size must be positive: $bytes"
    }

    @Test
    fun `rejects byte sizes that overflow a long`() {
        shouldThrow<ArithmeticException> {
            ByteSize.parse("${Long.MAX_VALUE}GiB")
        }
    }

    @Test
    fun `compares using the number of bytes`() {
        (ByteSize.parse("1MiB") < ByteSize.parse("2MiB")) shouldBe true
        (ByteSize.parse("1MB") < ByteSize.parse("1MiB")) shouldBe true
    }

    @Test
    fun `serializer uses an exact canonical byte count`() {
        val size = ByteSize.parse("5MiB")

        Json.encodeToString(ByteSizeSerializer, size) shouldBe "\"5242880B\""
        Json.decodeFromString(ByteSizeSerializer, "\"5242880B\"") shouldBe size
    }

    @OptIn(ExperimentalSerializationApi::class)
    @ParameterizedTest
    @CsvSource(
        "12345, 12345",
        "5mb, 5000000",
        "1GiB, 1073741824",
    )
    fun `deserializes byte sizes from HOCON`(
        input: String,
        expectedBytes: Long,
    ) {
        val properties =
            Hocon.decodeFromConfig<ByteSizeProperties>(
                ConfigFactory.parseString("max-size = $input"),
            )

        properties.maxSize shouldBe ByteSize(expectedBytes)
    }
}

@Serializable
private data class ByteSizeProperties(
    @SerialName("max-size")
    val maxSize: ByteSize,
)
