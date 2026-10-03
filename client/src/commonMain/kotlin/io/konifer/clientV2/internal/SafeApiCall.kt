package io.konifer.clientV2.internal

import io.konifer.client.KoniferResponse
import io.ktor.utils.io.CancellationException

internal inline fun <T> safeApiCall(apiCall: () -> KoniferResponse<T>): KoniferResponse<T> =
    try {
        apiCall()
    } catch (e: CancellationException) {
        // Always re-throw cancellation exceptions so coroutines can cancel!
        throw e
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        KoniferResponse.NetworkError(e)
    }
