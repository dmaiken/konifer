package io.konifer.domain.asset

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.InvalidImageException
import io.konifer.domain.image.fromMimeType
import io.konifer.domain.ports.MimeTypeDetector
import io.ktor.util.logging.KtorSimpleLogger

class FormatValidator(
    private val mimeTypeDetector: MimeTypeDetector,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    fun deriveValidImageFormat(content: ByteArray): ImageFormat {
        val mimeType = mimeTypeDetector.detect(content)
        if (!validate(mimeType)) {
            logger.error("Not an image type: $mimeType")
            throw InvalidImageException("Not an image type")
        }
        return ImageFormat.fromMimeType(mimeType)
    }

    private fun validate(mimeType: String): Boolean = mimeType.startsWith("image/")
}
