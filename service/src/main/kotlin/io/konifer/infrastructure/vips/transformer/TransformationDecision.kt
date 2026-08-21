package io.konifer.infrastructure.vips.transformer

sealed interface TransformationDecision {
    data class Apply(
        val requiredAlpha: AlphaRequirement,
        val requiredPixelAccess: PixelAccess,
    ) : TransformationDecision

    object Skip : TransformationDecision
}

enum class AlphaRequirement {
    PREMULTIPLIED,
    UN_PREMULTIPLIED,
    EITHER,
}

enum class PixelAccess {
    SEQUENTIAL,
    RANDOM,
}
