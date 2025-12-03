package io.image

import io.image.model.ImageFormat
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments

class ImageTestSources {
    companion object {
        @JvmStatic
        fun supportsPagedSource(): List<Arguments> =
            ImageFormat.entries
                .filter { it.vipsProperties.supportsPaging }
                .map { arguments(it) }

        @JvmStatic
        fun notSupportsPagedSource(): List<Arguments> =
            ImageFormat.entries
                .filter { !it.vipsProperties.supportsPaging }
                .map { arguments(it) }
    }
}
