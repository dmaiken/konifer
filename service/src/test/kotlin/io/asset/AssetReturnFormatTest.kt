package io.asset

import io.asset.context.ReturnFormat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class AssetReturnFormatTest {
    companion object {
        @JvmStatic
        fun fromQueryParamSource(): Stream<Arguments> =
            Stream.of(
                arguments("redirect", ReturnFormat.REDIRECT),
                arguments("metadata", ReturnFormat.METADATA),
                arguments("content", ReturnFormat.CONTENT),
                arguments("link", ReturnFormat.LINK),
                arguments("REDIRECT", ReturnFormat.REDIRECT),
                arguments("METADATA", ReturnFormat.METADATA),
                arguments("CONTENT", ReturnFormat.CONTENT),
                arguments("LINK", ReturnFormat.LINK),
            )
    }

    @ParameterizedTest
    @MethodSource("fromQueryParamSource")
    fun `fromQueryParam returns correct asset format`(
        string: String,
        expected: ReturnFormat,
    ) {
        ReturnFormat.fromQueryParam(string) shouldBe expected
    }

    @Test
    fun `fromQueryParam sets default type`() {
        ReturnFormat.fromQueryParam(null) shouldBe ReturnFormat.LINK
    }

    @Test
    fun `fromQueryParam throws exception on invalid value`() {
        shouldThrow<IllegalArgumentException> {
            ReturnFormat.fromQueryParam("invalid")
        }
    }
}
