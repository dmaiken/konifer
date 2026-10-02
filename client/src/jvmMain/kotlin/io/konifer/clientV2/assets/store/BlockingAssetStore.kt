package io.konifer.clientV2.assets.store

import io.konifer.clientV2.KoniferV2Result
import io.konifer.common.http.AssetResponse
import io.konifer.common.image.ImageFormat
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.util.function.Supplier

class BlockingBlankAssetAtPath internal constructor(
    private val selection: BlankAssetAtPath,
) {
    fun fromUrl(url: String): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.fromUrl(url))

    fun fromS3Arn(arn: String): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.fromS3Arn(arn))

    fun fromBytes(
        bytes: ByteArray,
        format: ImageFormat,
    ): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.fromBytes(bytes, format))

    /** [open] must return a fresh stream for each request; the stream is closed after reading. */
    fun fromInputStream(
        open: Supplier<InputStream>,
        format: ImageFormat,
    ): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.fromInputStream({ open.get() }, format))
}

class BlockingNewAssetAtPath internal constructor(
    private val selection: NewAssetAtPath,
) {
    fun withAlt(alt: String): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.withAlt(alt))

    fun withLabel(
        key: String,
        value: String,
    ): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.withLabel(key, value))

    fun withLabels(labels: Map<String, String>): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.withLabels(labels))

    fun withTag(tag: String): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.withTag(tag))

    fun withTags(tags: Set<String>): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.withTags(tags))

    fun store(): KoniferV2Result<AssetResponse> = runBlocking { selection.store() }
}
