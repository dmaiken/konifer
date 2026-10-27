package io.konifer.common.http

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ErrorResponseSerializationTest :
    StringSpec({
        val json = Json { explicitNulls = false }

        "error codes have stable string values" {
            ErrorCode.entries.forEach { code ->
                val response = ErrorResponse("Explanation", code)
                json.encodeToString(response) shouldBe """{"message":"Explanation","code":"${code.name}"}"""
                json.decodeFromString<ErrorResponse>(json.encodeToString(response)) shouldBe response
            }
        }

        "null messages remain omitted and readable" {
            val response = ErrorResponse(null, ErrorCode.SOURCE_UNAVAILABLE)
            json.encodeToString(response) shouldBe """{"code":"SOURCE_UNAVAILABLE"}"""
            json.decodeFromString<ErrorResponse>(json.encodeToString(response)) shouldBe response
        }

        "server responses require a code" {
            shouldThrow<SerializationException> {
                json.decodeFromString<ErrorResponse>("""{"message":"Explanation"}""")
            }
        }
    })
