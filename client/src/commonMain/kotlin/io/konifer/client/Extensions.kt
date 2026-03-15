package io.konifer.client

import io.konifer.common.http.ErrorResponse
import io.konifer.common.selector.ReturnFormat
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess

private const val PATH_SEPARATOR = "-"

suspend inline fun <reified T> HttpResponse.toKoniferResponse(): KoniferResponse<T> =
    when {
        status.isSuccess() -> KoniferResponse.Success(body())
        else ->
            KoniferResponse.HttpError(
                code = status.value,
                message = body<ErrorResponse>().message,
            )
    }

fun URLBuilder.appendQuerySelectors(
    returnFormat: ReturnFormat,
    querySelectors: QuerySelectors,
) {
    appendPathSegments(PATH_SEPARATOR, returnFormat.name.lowercase())
    when (querySelectors) {
        is QuerySelectors.EntryId -> {
            appendPathSegments("entry", querySelectors.entryId.toString())
        }
        is QuerySelectors.OrderBy -> {
            appendPathSegments(querySelectors.orderBy.name.lowercase())
        }
        is QuerySelectors.None -> { } // Nothing
    }
}
