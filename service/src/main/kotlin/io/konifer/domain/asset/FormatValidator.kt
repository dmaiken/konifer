package io.konifer.domain.asset

import io.konifer.common.image.ImageFormat
import io.konifer.domain.context.ContentTypeNotPermittedException
import io.konifer.domain.image.InvalidImageException
import io.konifer.domain.image.fromMimeType
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.MimeTypeDetector
import io.ktor.util.logging.KtorSimpleLogger

class FormatValidator(
    private val mimeTypeDetector: MimeTypeDetector,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun validateInputFormat(
        pathConfiguration: PathConfiguration,
        container: AssetDataContainer,
    ): ImageFormat {
        val content = container.peek(1024)
        val mimeType = mimeTypeDetector.detect(content)
        validateIsImage(mimeType)

        pathConfiguration.allowedContentTypes?.let {
            validateContentTypeAllowed(
                allowedContentTypes = it,
                mimeType = mimeType,
            )
        }

        return ImageFormat.fromMimeType(mimeType)
    }

    fun validateOutputFormat(
        allowedFormats: List<String>?,
        format: ImageFormat,
    ) {
        if (ImageFormat.FormatAction.OUTPUT !in format.allowedFor) {
            throw ContentTypeNotPermittedException("Content type: ${format.mimeType} not permitted ${ImageFormat.FormatAction.OUTPUT}")
        }
        allowedFormats?.let {
            validateContentTypeAllowed(
                allowedContentTypes = it,
                mimeType = format.mimeType,
            )
        }
    }

    private fun validateContentTypeAllowed(
        allowedContentTypes: List<String>,
        mimeType: String,
    ) {
        if (mimeType !in allowedContentTypes) {
            throw ContentTypeNotPermittedException("Content type: $mimeType not permitted")
        }
    }

    private fun validateIsImage(mimeType: String) {
        if (!mimeType.startsWith("image/")) {
            logger.error("Not an image type: $mimeType")
            throw InvalidImageException("Not an image type")
        }
    }
}
