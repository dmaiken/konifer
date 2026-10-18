package io.konifer.domain.asset

import io.konifer.common.image.ImageFormat
import io.konifer.domain.context.ContentTypeNotPermittedException
import io.konifer.domain.image.fromMimeType
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.MimeTypeDetector

class FormatValidator(
    private val mimeTypeDetector: MimeTypeDetector,
) {
    suspend fun validateInputFormat(
        pathConfiguration: PathConfiguration,
        container: AssetDataContainer,
    ): ImageFormat {
        val content = container.peek(1024)
        val mimeType = mimeTypeDetector.detect(content)
        val format = ImageFormat.fromMimeType(mimeType)

        validateFormatAllowed(
            allowedFormats = pathConfiguration.allowedFormats,
            format = format,
            action = ImageFormat.FormatAction.INPUT,
        )

        return format
    }

    fun validateOutputFormat(
        allowedFormats: List<ImageFormat>,
        format: ImageFormat,
    ) {
        validateFormatAllowed(allowedFormats, format, ImageFormat.FormatAction.OUTPUT)
    }

    private fun validateFormatAllowed(
        allowedFormats: List<ImageFormat>,
        format: ImageFormat,
        action: ImageFormat.FormatAction,
    ) {
        if (action !in format.allowedFor || format !in allowedFormats) {
            throw ContentTypeNotPermittedException("Image format: ${format.format} not permitted for $action")
        }
    }
}
