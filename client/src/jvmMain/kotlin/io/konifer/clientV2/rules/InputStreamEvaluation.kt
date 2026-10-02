package io.konifer.clientV2.rules

import io.konifer.clientV2.internal.inputStreamChunks
import io.konifer.common.image.ImageFormat
import java.io.InputStream

/** Opens a fresh stream for each evaluation attempt and closes it after reading. */
fun BlankRuleEvaluation.fromInputStream(
    open: () -> InputStream,
    format: ImageFormat,
): NewRuleEvaluation = fromChunks(inputStreamChunks(open), format)
