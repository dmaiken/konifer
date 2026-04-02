package io.konifer.client

import io.konifer.client.KoniferClient.Companion.LIMIT_PARAMETER
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
        else -> {
            val errorMessage = runCatching {
                body<ErrorResponse>().message
            }.getOrElse {
                "An unexpected server error occurred: ${status.description}"
            }
            KoniferResponse.HttpError(
                code = status.value,
                message = errorMessage,
            )
        }
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

fun URLBuilder.appendTransformationParameters(requestedTransformation: RequestedTransformation) {
    requestedTransformation.width?.let { width -> parameters.append("w", width.toString()) }
    requestedTransformation.height?.let { height -> parameters.append("h", height.toString()) }
    requestedTransformation.format?.let { format -> parameters.append("format", format.toString()) }
    requestedTransformation.fit?.let { fit -> parameters.append("fit", fit.toString()) }
    requestedTransformation.gravity?.let { gravity -> parameters.append("g", gravity.toString()) }
    requestedTransformation.rotate?.let { rotate -> parameters.append("r", rotate.toString()) }
    requestedTransformation.filter?.let { filter -> parameters.append("filter", filter.toString()) }
    requestedTransformation.blur?.let { blur -> parameters.append("blur", blur.toString()) }
    requestedTransformation.quality?.let { quality -> parameters.append("q", quality.toString()) }
    requestedTransformation.pad?.let { pad -> parameters.append("pad", pad.toString()) }
    requestedTransformation.padColor?.let { padColor -> parameters.append("pad-c", padColor) }
}
