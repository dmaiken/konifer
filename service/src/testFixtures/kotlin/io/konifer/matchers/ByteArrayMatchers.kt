package io.konifer.matchers

import io.konifer.common.image.ImageFormat
import io.konifer.infrastructure.tika.TikaMimeTypeDetector
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should

infix fun ByteArray.shouldBeFormat(format: ImageFormat) = this should beFormat(format)

fun beFormat(expected: ImageFormat): Matcher<ByteArray> =
    object : Matcher<ByteArray> {
        override fun test(value: ByteArray): MatcherResult {
            val mimeType = TikaMimeTypeDetector().detect(value)
            return MatcherResult(
                mimeType == expected.mimeType,
                { "Expected ${value.contentHashCode()} to be ${expected.mimeType} but was $mimeType" },
                { "Expected ${value.contentHashCode()} not to be ${expected.mimeType} but was $mimeType" },
            )
        }
    }
