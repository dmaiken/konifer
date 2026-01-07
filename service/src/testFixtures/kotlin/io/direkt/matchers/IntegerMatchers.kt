package io.direkt.matchers

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should

fun beWithinOneOf(expected: Int): Matcher<Int> =
    object : Matcher<Int> {
        override fun test(value: Int): MatcherResult {
            val passed = value in (expected - 1)..(expected + 1)
            return MatcherResult(
                passed,
                { "Expected $value to be within ±1 of $expected" },
                { "Expected $value to not be within ±1 of $expected" },
            )
        }
    }

infix fun Int.shouldBeWithinOneOf(expected: Int) = this should beWithinOneOf(expected)
