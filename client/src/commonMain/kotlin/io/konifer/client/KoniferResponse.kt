package io.konifer.client

import io.ktor.http.HttpStatusCode

sealed class KoniferResponse<out T> {
    data class Success<out T>(
        val body: T,
    ) : KoniferResponse<T>()

    data class HttpError(
        val httpStatusCode: HttpStatusCode,
        val message: String?,
    ) : KoniferResponse<Nothing>()

    data class NetworkError(
        val exception: Throwable,
    ) : KoniferResponse<Nothing>()
}

inline fun <T, R> KoniferResponse<T>.fold(
    onSuccess: (T) -> R,
    onError: (code: HttpStatusCode?, message: String?, exception: Throwable?) -> R,
): R =
    when (this) {
        is KoniferResponse.Success -> onSuccess(body)
        is KoniferResponse.HttpError -> onError(httpStatusCode, message, null)
        is KoniferResponse.NetworkError -> onError(null, null, exception)
    }
