package io.direkt.infrastructure.http

import io.direkt.domain.asset.AssetData
import io.direkt.domain.image.ImageFormat
import io.direkt.service.context.ReturnFormat
import io.ktor.http.HttpHeaders
import java.net.URLEncoder

const val APP_CACHE_STATUS = "App-Cache-Status"

fun getAppStatusCacheHeader(cacheHit: Boolean): Pair<String, String> {
    val value =
        if (cacheHit) {
            "hit"
        } else {
            "miss"
        }

    return Pair(APP_CACHE_STATUS, value)
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
