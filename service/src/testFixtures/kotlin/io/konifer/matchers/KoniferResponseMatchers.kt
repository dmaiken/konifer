package io.konifer.matchers

import io.konifer.client.KoniferResponse
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.types.beInstanceOf
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun <T> KoniferResponse<T>.shouldBeSuccessful(): KoniferResponse.Success<T> {
    contract {
        returns() implies (this@shouldBeSuccessful is KoniferResponse.Success<T>)
    }
    this should beInstanceOf(KoniferResponse.Success::class)

    @Suppress("UNCHECKED_CAST")
    return this as KoniferResponse.Success<T>
}

@OptIn(ExperimentalContracts::class)
infix fun <T> KoniferResponse<T>.shouldHaveHttpError(statusCode: Int): KoniferResponse.HttpError {
    contract {
        returns() implies (this@shouldHaveHttpError is KoniferResponse.HttpError)
    }
    this should haveStatusCode(statusCode)
    return this as KoniferResponse.HttpError
}

@OptIn(ExperimentalContracts::class)
fun <T> KoniferResponse<T>.shouldBeNetworkError(): KoniferResponse.NetworkError {
    contract {
        returns() implies (this@shouldBeNetworkError is KoniferResponse.NetworkError)
    }
    this should beInstanceOf(KoniferResponse.NetworkError::class)
    return this as KoniferResponse.NetworkError
}

fun haveStatusCode(expectedStatusCode: Int): Matcher<KoniferResponse<*>> =
    object : Matcher<KoniferResponse<*>> {
        override fun test(value: KoniferResponse<*>): MatcherResult =
            if (value::class == KoniferResponse.HttpError::class &&
                value is KoniferResponse.HttpError &&
                value.httpStatusCode == expectedStatusCode
            ) {
                MatcherResult(
                    passed = true,
                    { "" },
                    { "" },
                )
            } else {
                MatcherResult(
                    passed = false,
                    {
                        "Expected ${KoniferResponse.HttpError::class.simpleName} with status code $expectedStatusCode, but got ${value::class.simpleName}"
                    },
                    {
                        "Did not expect ${KoniferResponse.HttpError::class.simpleName} with status code $expectedStatusCode, but got ${value::class.simpleName}"
                    },
                )
            }
    }
