package io.konifer.matchers

import io.konifer.client.KoniferResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

fun <T> KoniferResult<T>.shouldBeSuccessful(): KoniferResult.Success<T> =
    when (this) {
        is KoniferResult.Success -> this
        is KoniferResult.Failure -> error("Expected a successful Konifer response, but received $this")
    }

infix fun KoniferResult<*>.shouldHaveHttpError(statusCode: Int): KoniferResult.Failure.Http {
    val failure = shouldBeInstanceOf<KoniferResult.Failure.Http>()
    failure.statusCode shouldBe statusCode
    return failure
}

fun KoniferResult<*>.shouldBeNetworkError(): KoniferResult.Failure.Transport = shouldBeInstanceOf<KoniferResult.Failure.Transport>()
