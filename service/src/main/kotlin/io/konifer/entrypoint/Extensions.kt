package io.konifer.entrypoint

import io.konifer.application.usecase.fetch.FetchAssetHandler
import io.konifer.domain.variant.LQIPs
import io.konifer.infrastructure.http.CustomAttributes.entryIdKey
import io.konifer.infrastructure.http.CustomAttributes.lastModifiedKey
import io.konifer.infrastructure.http.getAltHeader
import io.konifer.infrastructure.http.getAppStatusCacheHeader
import io.konifer.infrastructure.http.getLqipHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.asSink
import io.ktor.utils.io.copyTo
import java.time.LocalDateTime

suspend fun ApplicationCall.respondContent(
    objectStoreBucket: String,
    objectStoreKey: String,
    cacheHit: Boolean,
    alt: String?,
    lqips: LQIPs,
    entryId: Long,
    modifiedAt: LocalDateTime,
    mimeType: String,
    fetchAssetHandler: FetchAssetHandler,
) {
    getAppStatusCacheHeader(cacheHit).let {
        this.response.headers.append(it.first, it.second)
    }
    getAltHeader(alt)?.let {
        this.response.headers.append(it.first, it.second)
    }
    getLqipHeaders(lqips).forEach {
        this.response.headers.append(it.first, it.second)
    }
    // Populate attributes used for etag creation
    this.attributes[entryIdKey] = entryId
    this.attributes[lastModifiedKey] = modifiedAt

    this.respondBytesWriter(
        contentType =
            ContentType.parse(mimeType),
        status = HttpStatusCode.OK,
    ) {
        fetchAssetHandler.fetchContent(
            bucket = objectStoreBucket,
            storeKey = objectStoreKey,
            stream = this,
        )
    }
}

suspend fun PartData.copyAssetContentTo(assetContentChannel: ByteChannel): Boolean {
    try {
        when (this) {
            is PartData.FileItem -> provider().copyTo(assetContentChannel)
            is PartData.BinaryChannelItem -> provider().copyTo(assetContentChannel)
            is PartData.BinaryItem -> provider().transferTo(assetContentChannel.asSink())
            else -> return false
        }
        return true
    } finally {
        assetContentChannel.close()
        release()
    }
}
