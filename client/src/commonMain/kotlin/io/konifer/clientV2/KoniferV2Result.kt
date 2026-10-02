package io.konifer.clientV2

import io.konifer.common.http.ErrorResponse
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.ReceivePipelineException
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

sealed interface KoniferV2Result<out T> {
    data class Success<out T>(
        val value: T,
    ) : KoniferV2Result<T>

    sealed interface Failure : KoniferV2Result<Nothing> {
        data class Http(
            val statusCode: Int,
            val message: String?,
        ) : Failure

        data class Transport(
            val cause: Throwable,
        ) : Failure

        data class InvalidResponse(
            val cause: Throwable,
        ) : Failure
    }
}

inline fun <T, R> KoniferV2Result<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (KoniferV2Result.Failure) -> R,
): R =
    when (this) {
        is KoniferV2Result.Success -> onSuccess(value)
        is KoniferV2Result.Failure -> onFailure(this)
    }

internal suspend inline fun <reified T> HttpResponse.toKoniferV2Result(): KoniferV2Result<T> {
    if (status.isSuccess()) {
        return try {
            KoniferV2Result.Success(body<T>())
        } catch (e: Exception) {
            e.toResponseReadFailure()
        }
    }

    val errorMessage =
        try {
            body<ErrorResponse>().message
        } catch (e: Exception) {
            when (val failure = e.toResponseReadFailure()) {
                is KoniferV2Result.Failure.InvalidResponse -> null
                is KoniferV2Result.Failure.Transport -> return failure
                is KoniferV2Result.Failure.Http -> error("An HTTP failure cannot result from reading a response body")
            }
        }

    return KoniferV2Result.Failure.Http(
        statusCode = status.value,
        message = errorMessage,
    )
}

internal fun Throwable.toResponseReadFailure(): KoniferV2Result.Failure =
    when (this) {
        is CancellationException -> {
            throw this
        }

        is ReceivePipelineException -> {
            cause.toResponseReadFailure()
        }

        is IOException -> {
            KoniferV2Result.Failure.Transport(this)
        }

        is ContentConvertException, is SerializationException, is NoTransformationFoundException -> {
            KoniferV2Result.Failure.InvalidResponse(this)
        }

        else -> {
            throw this
        }
    }
