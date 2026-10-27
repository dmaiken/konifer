package io.konifer.client

import io.konifer.common.http.ErrorCode
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.ReceivePipelineException
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Result of a Konifer client operation.
 *
 * The client returns expected HTTP, transport, and response-decoding failures as [Failure].
 * Coroutine cancellation and unexpected programming errors remain exceptions.
 */
sealed interface KoniferResult<out T> {
    /** A successful operation containing its decoded [value]. */
    data class Success<out T>(
        val value: T,
    ) : KoniferResult<T>

    /** A failure produced while sending a request or reading its response. */
    sealed interface Failure : KoniferResult<Nothing> {
        /** A non-success HTTP response from Konifer. */
        data class Http(
            /** Numeric HTTP status code. */
            val statusCode: Int,
            /** Error message decoded from the response body, when available. */
            val message: String?,
            /** Stable error category, or UNKNOWN when the response has no recognized code. */
            val code: ErrorCode = ErrorCode.UNKNOWN,
        ) : Failure

        /** A network or response-body I/O failure. */
        data class Transport(
            val cause: Throwable,
        ) : Failure

        /** A response that could not be decoded as the expected type. */
        data class InvalidResponse(
            val cause: Throwable,
        ) : Failure
    }

    /**
     * Return the value if result is [Success], otherwise throw a [KoniferRequestException].
     */
    fun valueOrThrow(): T =
        when (this) {
            is Success -> value
            is Failure -> throw KoniferRequestException(this)
        }
}

/** Applies [onSuccess] or [onFailure] and returns the selected callback's result. */
inline fun <T, R> KoniferResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (KoniferResult.Failure) -> R,
): R =
    when (this) {
        is KoniferResult.Success -> onSuccess(value)
        is KoniferResult.Failure -> onFailure(this)
    }

internal suspend inline fun <reified T> HttpResponse.toKoniferResult(): KoniferResult<T> {
    if (status.isSuccess()) {
        return try {
            KoniferResult.Success(body<T>())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toResponseReadFailure()
        }
    }

    val errorBody =
        try {
            body<ErrorBody>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when (val failure = e.toResponseReadFailure()) {
                is KoniferResult.Failure.InvalidResponse -> null
                is KoniferResult.Failure.Transport -> return failure
                is KoniferResult.Failure.Http -> error("An HTTP failure cannot result from reading a response body")
            }
        }

    return KoniferResult.Failure.Http(
        statusCode = status.value,
        message = errorBody?.message,
        code = ErrorCode.entries.firstOrNull { it.name == errorBody?.code } ?: ErrorCode.UNKNOWN,
    )
}

internal fun Throwable.toResponseReadFailure(): KoniferResult.Failure =
    when (this) {
        is CancellationException -> {
            throw this
        }

        is ReceivePipelineException -> {
            cause.toResponseReadFailure()
        }

        is IOException -> {
            KoniferResult.Failure.Transport(this)
        }

        is ContentConvertException, is SerializationException, is NoTransformationFoundException -> {
            KoniferResult.Failure.InvalidResponse(this)
        }

        else -> {
            throw this
        }
    }

// Decode codes as strings so older SDKs can still read messages from newer servers.
@Serializable
internal data class ErrorBody(
    val message: String? = null,
    val code: String? = null,
)
