package io.konifer.matchers

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import kotlin.math.abs

infix fun Double.shouldBeApproximately(expected: Double) = this should beApproximately(expected)

fun beApproximately(
    expected: Double,
    epsilon: Double = 1e-2,
): Matcher<Double> =
    object : Matcher<Double> {
        override fun test(value: Double): MatcherResult {
            val diff = abs(value - expected)
            return MatcherResult(
                diff < epsilon,
                { "Expected $value to be within $epsilon of $expected but was $diff" },
                { "Expected $value to not be within $epsilon of $expected" },
            )
        }
    }
