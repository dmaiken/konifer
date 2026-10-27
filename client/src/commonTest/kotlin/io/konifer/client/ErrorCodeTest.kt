package io.konifer.client

import io.konifer.common.http.ErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

class ErrorCodeTest :
    FunSpec({
        val cases =
            listOf(
                Triple("""{"code":"INVALID_PATH","message":"Bad path"}""", ErrorCode.INVALID_PATH, "Bad path"),
                Triple("""{"code":"FUTURE_CODE","message":"Future failure"}""", ErrorCode.UNKNOWN, "Future failure"),
                Triple("""{"message":"Legacy failure"}""", ErrorCode.UNKNOWN, "Legacy failure"),
                Triple("""{"code":null,"message":"No code"}""", ErrorCode.UNKNOWN, "No code"),
                Triple("""{"code":"INVALID_PATH"}""", ErrorCode.INVALID_PATH, null),
                Triple("""{"code":"INVALID_PATH","message":null}""", ErrorCode.INVALID_PATH, null),
                Triple("{}", ErrorCode.UNKNOWN, null),
                Triple("", ErrorCode.UNKNOWN, null),
                Triple("not-json", ErrorCode.UNKNOWN, null),
            )
        cases.forEach { (body, code, message) ->
            test("HTTP errors retain status and decode $body") {
                val engine =
                    MockEngine {
                        respond(body, HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                val client = KoniferClient.build("https://konifer.example", engine = engine)
                try {
                    val failure = client.assets("avatars").fetchInfo()
                    failure shouldBe KoniferResult.Failure.Http(400, message, code)
                    shouldThrow<KoniferRequestException> { failure.valueOrThrow() }.failure shouldBe failure
                } finally {
                    client.close()
                    engine.close()
                }
            }
        }
    })
