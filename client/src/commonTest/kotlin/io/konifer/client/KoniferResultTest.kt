package io.konifer.client

import io.konifer.client.asset.link.createLinkResponse
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class KoniferResultTest :
    FunSpec({

        test("valueOrThrow returns the value when successful") {
            val response = createLinkResponse()

            shouldNotThrowAny {
                KoniferResult.Success(response).valueOrThrow()
            } shouldBe response
        }

        test("valueOrThrow throws exception when http failure") {
            val exception =
                shouldThrow<KoniferRequestException> {
                    KoniferResult.Failure.Http(400, "Bad request!").valueOrThrow()
                }
            exception.message shouldBe "Konifer returned HTTP 400: Bad request!"
            exception.cause shouldBe null
        }

        test("valueOrThrow throws exception when transport failure") {
            val cause = IOException("Something bad")
            val exception =
                shouldThrow<KoniferRequestException> {
                    KoniferResult.Failure.Transport(cause).valueOrThrow()
                }
            exception.message shouldBe "Konifer request failed during transport"
            exception.cause shouldBe cause
        }

        test("valueOrThrow throws exception when invalid response failure") {
            val cause = IOException("Something bad")
            val exception =
                shouldThrow<KoniferRequestException> {
                    KoniferResult.Failure.InvalidResponse(cause).valueOrThrow()
                }
            exception.message shouldBe "Konifer response could not be decoded"
            exception.cause shouldBe cause
        }

        test("fold calls success handler when successful") {
            val counter = AtomicInteger(0)

            KoniferResult.Success(createLinkResponse()).fold(
                onSuccess = { counter.incrementAndGet() },
                onFailure = { fail("Should have called success handler!") },
            )

            counter.get() shouldBe 1
        }

        test("fold calls failure handler when not successful") {
            val counter = AtomicInteger(0)

            KoniferResult.Failure.Http(200, "Something bad!").fold(
                onSuccess = { fail("Should have called failure handler!") },
                onFailure = { counter.incrementAndGet() },
            )

            counter.get() shouldBe 1
        }
    })
