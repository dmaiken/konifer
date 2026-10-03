package io.konifer.client.assets.store

import io.konifer.client.internal.inputStreamChunks
import io.konifer.common.image.ImageFormat
import java.io.InputStream

/**
 * Streams an image from a fresh [InputStream] for each upload attempt and closes each stream after
 * reading it.
 *
 * @param open supplier that must return a new readable stream on each invocation.
 * @param format image format advertised for the uploaded bytes.
 */
fun BlankAssetAtPath.fromInputStream(
    open: () -> InputStream,
    format: ImageFormat,
): NewAssetAtPath = fromChunks(inputStreamChunks(open), format)
