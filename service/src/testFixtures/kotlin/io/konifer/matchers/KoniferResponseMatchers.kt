package io.konifer.matchers

import io.konifer.clientV2.KoniferV2Result
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

fun <T> KoniferV2Result<T>.shouldBeSuccessful(): KoniferV2Result.Success<T> =
    when (this) {
        is KoniferV2Result.Success -> this
        is KoniferV2Result.Failure -> error("Expected a successful Konifer response, but received $this")
    }

infix fun KoniferV2Result<*>.shouldHaveHttpError(statusCode: Int): KoniferV2Result.Failure.Http {
    val failure = shouldBeInstanceOf<KoniferV2Result.Failure.Http>()
    failure.statusCode shouldBe statusCode
    return failure
}

fun KoniferV2Result<*>.shouldBeNetworkError(): KoniferV2Result.Failure.Transport = shouldBeInstanceOf<KoniferV2Result.Failure.Transport>()
