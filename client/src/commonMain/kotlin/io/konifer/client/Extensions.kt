package io.konifer.client

import io.konifer.common.http.ErrorResponse
import io.konifer.common.image.ManipulationParameters.BLUR
import io.konifer.common.image.ManipulationParameters.FILTER
import io.konifer.common.image.ManipulationParameters.FIT
import io.konifer.common.image.ManipulationParameters.FLIP
import io.konifer.common.image.ManipulationParameters.FORMAT
import io.konifer.common.image.ManipulationParameters.GRAVITY
import io.konifer.common.image.ManipulationParameters.HEIGHT
import io.konifer.common.image.ManipulationParameters.PAD
import io.konifer.common.image.ManipulationParameters.PAD_COLOR
import io.konifer.common.image.ManipulationParameters.QUALITY
import io.konifer.common.image.ManipulationParameters.ROTATE
import io.konifer.common.image.ManipulationParameters.VARIANT_PROFILE
import io.konifer.common.image.ManipulationParameters.WIDTH
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
            val errorMessage =
                runCatching {
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
    requestedTransformation.width?.let { width -> parameters.append(WIDTH, width.toString()) }
    requestedTransformation.height?.let { height -> parameters.append(HEIGHT, height.toString()) }
    requestedTransformation.format?.let { format -> parameters.append(FORMAT, format.queryParameterValue) }
    requestedTransformation.fit?.let { fit -> parameters.append(FIT, fit.queryParameterValue) }
    requestedTransformation.flip?.let { flip -> parameters.append(FLIP, flip.queryParameterValue) }
    requestedTransformation.gravity?.let { gravity -> parameters.append(GRAVITY, gravity.queryParameterValue) }
    requestedTransformation.rotate?.let { rotate -> parameters.append(ROTATE, rotate.queryParameterValue) }
    requestedTransformation.filter?.let { filter -> parameters.append(FILTER, filter.queryParameterValue) }
    requestedTransformation.blur?.let { blur -> parameters.append(BLUR, blur.toString()) }
    requestedTransformation.quality?.let { quality -> parameters.append(QUALITY, quality.toString()) }
    requestedTransformation.pad?.let { pad -> parameters.append(PAD, pad.toString()) }
    requestedTransformation.padColor?.let { padColor -> parameters.append(PAD_COLOR, padColor) }
    requestedTransformation.profile?.let { profile -> parameters.append(VARIANT_PROFILE, profile) }
}
