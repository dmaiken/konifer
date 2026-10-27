package io.konifer.entrypoint

import io.konifer.application.usecase.fetch.AssetContent
import io.konifer.common.http.AttributesResponse
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.infrastructure.http.CustomAttributes.lastModifiedKey
import io.konifer.infrastructure.http.CustomAttributes.variantIdKey
import io.konifer.infrastructure.http.fromAttributes
import io.konifer.infrastructure.http.getAltHeader
import io.konifer.infrastructure.http.getAppStatusCacheHeader
import io.konifer.infrastructure.http.getAttributeHeaders
import io.konifer.infrastructure.http.getContentDispositionHeader
import io.konifer.infrastructure.http.getLqipHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytesWriter

suspend fun ApplicationCall.respondContent(
    content: AssetContent,
    returnFormat: ReturnFormat,
) {
    val (asset, variant, cacheResult) = content.delivery
    val variantAttributes = variant.attributes

    getContentDispositionHeader(
        alt = asset.alt,
        path = asset.path,
        returnFormat = returnFormat,
        imageFormat = variantAttributes.format,
    )?.let {
        this.response.headers.append(it.first, it.second)
    }
    getAppStatusCacheHeader(cacheResult).let {
        this.response.headers.append(it.first, it.second)
    }
    getAltHeader(asset.alt)?.let {
        this.response.headers.append(it.first, it.second)
    }
    getLqipHeaders(variant.lqips).forEach {
        this.response.headers.append(it.first, it.second)
    }
    getAttributeHeaders(AttributesResponse.fromAttributes(variantAttributes)).forEach {
        this.response.headers.append(it.first, it.second)
    }
    // Identify the selected representation without reading its content.
    this.attributes[variantIdKey] = variant.id
    this.attributes[lastModifiedKey] = asset.modifiedAt

    this.respondBytesWriter(
        contentType =
            ContentType.parse(variantAttributes.format.mimeType),
        status = HttpStatusCode.OK,
    ) {
        content.writeTo(this)
    }
}

suspend fun PartData.copyAssetContentToTemporaryFile(): AssetDataContainer? {
    val source =
        when (this) {
            is PartData.FileItem -> {
                provider()
            }

            else -> {
                release()
                return null
            }
        }
    val container = AssetDataContainer(channel = source)

    try {
        // The format is detected after all multipart fields have been parsed. Processing APIs receive
        // the detected format explicitly, so the temporary upload does not require an extension.
        container.toTemporaryFile("")
        return container
    } catch (e: Throwable) {
        container.close()
        throw e
    } finally {
        release()
    }
}
