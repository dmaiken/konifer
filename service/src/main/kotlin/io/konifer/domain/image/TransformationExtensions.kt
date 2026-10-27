package io.konifer.domain.image

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.Rotate
import io.konifer.common.image.Rotate.NINETY
import io.konifer.common.image.Rotate.ONE_HUNDRED_EIGHTY
import io.konifer.common.image.Rotate.TWO_HUNDRED_SEVENTY
import io.konifer.common.image.Rotate.ZERO
import io.konifer.common.image.TransformableColorSpace
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.transformation.InvalidTransformationException
import io.ktor.http.Parameters

fun Filter.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): Filter? = parameters[parameterName]?.let { enumValue<Filter>(it) }

fun Fit.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): Fit? =
    parameters[parameterName]?.let {
        enumValue<Fit>(it)
    }

fun Flip.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): Flip? = parameters[parameterName]?.let { enumValue<Flip>(it) }

fun Gravity.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): Gravity? =
    parameters[parameterName]?.let {
        enumValue<Gravity>(it)
    }

fun Rotate.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): Rotate? =
    parameters[parameterName]?.let {
        toRotate(it)
    }

private fun toRotate(value: String): Rotate =
    value.toIntOrNull()?.let {
        when (it) {
            0 -> ZERO
            90 -> NINETY
            180 -> ONE_HUNDRED_EIGHTY
            270 -> TWO_HUNDRED_SEVENTY
            else -> throw InvalidTransformationException("Invalid rotation: $value. Must be increments of 90")
        }
    } ?: enumValue<Rotate>(value)

fun TransformableColorSpace.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): TransformableColorSpace? = parameters[parameterName]?.let { enumValue<TransformableColorSpace>(it) }

fun PixelLayout.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): PixelLayout? = parameters[parameterName]?.let { enumValue<PixelLayout>(it) }

private inline fun <reified T : Enum<T>> enumValue(value: String): T =
    enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: throw InvalidTransformationException("Invalid ${T::class.simpleName}: $value")
