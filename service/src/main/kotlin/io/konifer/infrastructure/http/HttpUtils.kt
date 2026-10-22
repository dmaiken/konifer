package io.konifer.infrastructure.http

import io.konifer.application.usecase.fetch.CacheResult
import io.konifer.common.http.AssetContentHeaders
import io.konifer.common.http.AttributesResponse
import io.konifer.common.image.ImageFormat
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.asset.AssetData
import io.konifer.domain.variant.LQIPs
import io.ktor.http.HttpHeaders
import java.net.URLEncoder

const val APP_CACHE_STATUS = "K-Cache-Status"
const val APP_LQIP_BLURHASH = AssetContentHeaders.BLURHASH
const val APP_LQIP_THUMBHASH = AssetContentHeaders.THUMBHASH
const val APP_ALT = AssetContentHeaders.ALT

fun getAttributeHeaders(attributes: AttributesResponse): List<Pair<String, String>> =
    buildList {
        add(AssetContentHeaders.WIDTH to attributes.width.toString())
        add(AssetContentHeaders.HEIGHT to attributes.height.toString())
        add(AssetContentHeaders.FORMAT to attributes.format)
        add(AssetContentHeaders.ROTATE to attributes.rotate.queryParameterValue)
        add(AssetContentHeaders.HORIZONTAL_FLIP to attributes.horizontalFlip.toString())
        add(AssetContentHeaders.COLOR_SPACE to attributes.colorSpace)
        attributes.pageCount?.let { add(AssetContentHeaders.PAGE_COUNT to it.toString()) }
        attributes.loop?.let { add(AssetContentHeaders.LOOP to it.toString()) }
        attributes.pixels?.let {
            add(AssetContentHeaders.PIXELS_CHANNELS to it.channels)
            add(AssetContentHeaders.PIXELS_LAYOUT to it.layout.queryParameterValue)
            add(AssetContentHeaders.PIXELS_DATA_TYPE to it.dataType.queryParameterValue)
        }
    }

fun getAppStatusCacheHeader(cacheResult: CacheResult): Pair<String, String> {
    val value =
        when (cacheResult) {
            CacheResult.HIT -> "hit"
            CacheResult.MISS -> "miss"
        }

    return Pair(APP_CACHE_STATUS, value)
}

fun getLqipHeaders(lqiPs: LQIPs): List<Pair<String, String>> =
    buildList {
        if (lqiPs.blurhash != null) {
            add(Pair(APP_LQIP_BLURHASH, lqiPs.blurhash))
        }
        if (lqiPs.thumbhash != null) {
            add(Pair(APP_LQIP_THUMBHASH, lqiPs.thumbhash))
        }
    }

fun getAltHeader(alt: String?): Pair<String, String>? =
    alt?.let {
        Pair(APP_ALT, it)
    }

/**
 * If the [returnFormat] is [ReturnFormat.DOWNLOAD], construct a Content-Disposition header value
 * to tell the browser to download the asset. The filename will be the alt of the asset or, if not present,
 * the path of the asset.
 */
fun getContentDispositionHeader(
    asset: AssetData,
    returnFormat: ReturnFormat,
    imageFormat: ImageFormat,
): Pair<String, String>? {
    if (returnFormat != ReturnFormat.DOWNLOAD) {
        return null
    }

    val encodedFileName =
        URLEncoder.encode(
            asset.alt ?: asset.path,
            Charsets.UTF_8,
        )
    // The URLEncoder might encode spaces as '+', which is technically incorrect
    // for this specific header context, so we replace '+' with '%20'.
    // While browsers are often forgiving, this ensures strict compliance.
    val correctedEncodedFileName = encodedFileName.replace("+", "%20")

    return Pair(
        HttpHeaders.ContentDisposition,
        "attachment; filename*=UTF-8''$correctedEncodedFileName${imageFormat.extension}",
    )
}
