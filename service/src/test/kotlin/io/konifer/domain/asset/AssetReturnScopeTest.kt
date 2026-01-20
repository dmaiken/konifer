package io.konifer.domain.asset

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class AssetReturnScopeTest {
    companion object {
        @JvmStatic
        fun fromQueryParamSource(): Stream<Arguments> =
            Stream.of(
                Arguments.arguments("single", AssetReturnScope.SINGLE),
                Arguments.arguments("shallow", AssetReturnScope.SHALLOW),
                Arguments.arguments("recursive", AssetReturnScope.RECURSIVE),
                Arguments.arguments("SINGLE", AssetReturnScope.SINGLE),
                Arguments.arguments("SHALLOW", AssetReturnScope.SHALLOW),
                Arguments.arguments("RECURSIVE", AssetReturnScope.RECURSIVE),
            )
    }

    @ParameterizedTest
    @MethodSource("fromQueryParamSource")
    fun `fromQueryParam returns correct asset return scope`(
        string: String,
        expected: AssetReturnScope,
    ) {
        AssetReturnScope.fromQueryParam(string) shouldBe expected
    }

    @Test
    fun `fromQueryParam sets default type`() {
        AssetReturnScope.fromQueryParam(null) shouldBe AssetReturnScope.SINGLE
    }

    @Test
    fun `fromQueryParam throws exception on invalid value`() {
        shouldThrow<IllegalArgumentException> {
            AssetReturnScope.fromQueryParam("invalid")
        }
    }
}
