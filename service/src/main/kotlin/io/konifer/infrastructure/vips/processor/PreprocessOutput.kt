package io.konifer.infrastructure.vips.processor

sealed interface PreprocessOutput {
    object SourceTransformed : PreprocessOutput

    object SourceNotTransformed : PreprocessOutput
}
