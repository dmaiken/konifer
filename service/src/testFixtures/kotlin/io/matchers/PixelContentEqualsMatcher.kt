package io.matchers

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import java.awt.image.BufferedImage

infix fun BufferedImage.shouldHaveSamePixelContentAs(expected: BufferedImage) = this should haveSamePixelContent(expected)

fun haveSamePixelContent(expected: BufferedImage): Matcher<BufferedImage> =
    object : Matcher<BufferedImage> {
        override fun test(value: BufferedImage): MatcherResult {
            if (expected.width != value.width || expected.height != value.height) {
                return MatcherResult(
                    passed = false,
                    {
                        "Expected actual to have same width (${value.width}) and height (${value.height}) " +
                            "as expected (${expected.width},${expected.height})"
                    },
                    {
                        "Expected actual to have not same width (${value.width}) and height (${value.height}) " +
                            "as expected (${expected.width},${expected.height})"
                    },
                )
            }

            for (y in 0 until expected.height) {
                for (x in 0 until expected.width) {
                    if (expected.getRGB(x, y) != value.getRGB(x, y)) {
                        return MatcherResult(
                            passed = false,
                            { "Expected actual to have same pixel content as expected" },
                            { "Expected actual not to have same pixel content as expected" },
                        )
                    }
                }
            }
            return MatcherResult(
                passed = true,
                { "Expected actual to have same pixel content as expected" },
                { "Expected actual not to have same pixel content as expected" },
            )
        }
    }
