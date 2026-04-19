package io.konifer.domain.image

import io.konifer.common.image.Filter
import io.konifer.common.image.Filter.valueOf
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.Rotate
import io.konifer.common.image.Rotate.NINETY
import io.konifer.common.image.Rotate.ONE_HUNDRED_EIGHTY
import io.konifer.common.image.Rotate.TWO_HUNDRED_SEVENTY
import io.konifer.common.image.Rotate.ZERO
import io.ktor.http.Parameters

fun Filter.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): Filter? = parameters[parameterName]?.let { valueOf(it.uppercase()) }

fun Filter.Factory.fromString(string: String?): Filter =
    string?.let {
        valueOf(it.uppercase())
    } ?: Filter.default

fun Fit.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): Fit? =
    parameters[parameterName]?.let {
        Fit.valueOf(it.uppercase())
    }

fun Fit.Factory.fromString(string: String?): Fit =
    string?.let {
        Fit.valueOf(string.uppercase())
    } ?: default

fun Flip.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): Flip? = parameters[parameterName]?.let { Flip.valueOf(it.uppercase()) }

fun Flip.Factory.fromString(string: String?): Flip =
    string?.let {
        Flip.valueOf(it.uppercase())
    } ?: default

fun Gravity.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): Gravity? =
    parameters[parameterName]?.let {
        Gravity.valueOf(it.uppercase())
    }

fun Gravity.Factory.fromString(string: String?): Gravity =
    string?.let {
        Gravity.valueOf(string.uppercase())
    } ?: default

fun Rotate.Factory.fromQueryParameters(
    parameters: Parameters,
    parameterName: String,
): Rotate? =
    parameters[parameterName]?.let {
        toRotate(it)
    }

fun Rotate.Factory.fromString(string: String?): Rotate =
    string?.let {
        toRotate(it)
    } ?: default

private fun toRotate(value: String): Rotate =
    value.toIntOrNull()?.let {
        when (it) {
            0 -> ZERO
            90 -> NINETY
            180 -> ONE_HUNDRED_EIGHTY
            270 -> TWO_HUNDRED_SEVENTY
            else -> throw IllegalArgumentException("Invalid rotation: $value. Must be increments of 90")
        }
    } ?: Rotate.valueOf(value.uppercase())
