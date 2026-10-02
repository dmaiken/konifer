package io.konifer.clientV2.assets.store

import io.konifer.clientV2.internal.inputStreamChunks
import io.konifer.common.image.ImageFormat
import java.io.InputStream

/** Opens a fresh stream for each upload attempt and closes it after reading. */
fun BlankAssetAtPath.fromInputStream(
    open: () -> InputStream,
    format: ImageFormat,
): NewAssetAtPath = fromChunks(inputStreamChunks(open), format)
