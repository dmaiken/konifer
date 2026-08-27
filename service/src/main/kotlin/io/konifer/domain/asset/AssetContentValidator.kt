package io.konifer.domain.asset

import io.konifer.common.image.ImageFormat
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.InvalidAssetSourceException
import io.konifer.domain.variant.Attributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

class AssetContentValidator(
    private val formatValidator: FormatValidator,
) {
    suspend fun validateAssetContent(
        pathConfiguration: PathConfiguration,
        container: AssetDataContainer,
    ): Attributes {
        val imageFormat =
            formatValidator.validateImageFormat(
                pathConfiguration = pathConfiguration,
                container = container,
            )

        container.toTemporaryFile("")

        return validateAttributes(
            sourceFile = container.getTemporaryFile(),
            format = imageFormat,
            limits = pathConfiguration.limits,
        )
    }

    private suspend fun validateAttributes(
        sourceFile: Path,
        format: ImageFormat,
        limits: AssetLimitProperties,
    ): Attributes =
        withContext(Dispatchers.IO) {
            val attributes =
                Attributes.createAttributes(
                    path = sourceFile,
                    format = format,
                )
            if (attributes.height > limits.maxHeight || attributes.width > limits.maxWidth) {
                throw InvalidAssetSourceException(
                    "Content with dimensions (${attributes.height.value}, ${attributes.width.value}) exceeds maximum defined dimensions",
                )
            }

            val maxPixels = if (attributes.pageCount > 1) limits.maxPixelsPerPage else limits.maxPixels
            val pixelCount = attributes.height.value.toLong() * attributes.width.value.toLong()
            if (attributes.height.value.toLong() * attributes.width.value.toLong() > maxPixels.value) {
                throw InvalidAssetSourceException("Content with pixel count $pixelCount exceeds maximum pixel amount")
            }

            if (attributes.pageCount > 1 && attributes.pageCount > limits.maxPages.value) {
                throw InvalidAssetSourceException(
                    "Animated content with page count ${attributes.pageCount} exceeds maximum defined page count",
                )
            }

            attributes
        }
}
