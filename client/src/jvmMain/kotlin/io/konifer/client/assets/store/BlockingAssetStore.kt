package io.konifer.client.assets.store

import io.konifer.client.KoniferResult
import io.konifer.common.http.AssetResponse
import io.konifer.common.image.ImageFormat
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.util.function.Supplier

/** Blocking source-selection stage for storing a new asset. */
class BlockingBlankAssetAtPath internal constructor(
    private val selection: BlankAssetAtPath,
) {
    /** Uses the image at [url] as the new asset's source. */
    fun fromUrl(url: String): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.fromUrl(url))

    /** Uses the S3 object identified by [s3Arn] as the new asset's source. */
    fun fromS3Arn(s3Arn: String): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.fromS3Arn(s3Arn))

    /** Uses a snapshot of [bytes] as the new asset's source. */
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

/** Blocking immutable request for storing a new asset. */
class BlockingNewAssetAtPath internal constructor(
    private val selection: NewAssetAtPath,
) {
    /** Returns an independent request with [alt] as the image's alternative text. */
    fun withAlt(alt: String): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.withAlt(alt))

    /** Adds or replaces the label identified by [key]. */
    fun withLabel(
        key: String,
        value: String,
    ): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.withLabel(key, value))

    /** Adds [labels], replacing values for keys already present in the request. */
    fun withLabels(labels: Map<String, String>): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.withLabels(labels))

    /** Adds [tag] to the new asset. */
    fun withTag(tag: String): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.withTag(tag))

    /** Adds [tags] to the new asset. */
    fun withTags(tags: Set<String>): BlockingNewAssetAtPath = BlockingNewAssetAtPath(selection.withTags(tags))

    /** Stores the image and metadata as a new asset entry. */
    fun store(): KoniferResult<AssetResponse> = runBlocking { selection.store() }
}
