package io.konifer.client

sealed class KoniferResponse<out T> {
    data class Success<out T>(
        val body: T,
    ) : KoniferResponse<T>()

    data class HttpError(
        val code: Int,
        val message: String?,
    ) : KoniferResponse<Nothing>()

    data class NetworkError(
        val exception: Throwable,
    ) : KoniferResponse<Nothing>()
}
