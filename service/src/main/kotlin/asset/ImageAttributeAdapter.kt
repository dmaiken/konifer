package io.asset

import asset.AssetReturnFormat
import image.model.RequestedImageAttributes
import io.asset.ContentParameters.RETURN
import io.asset.ManipulationParameters.HEIGHT
import io.asset.ManipulationParameters.MIME_TYPE
import io.asset.ManipulationParameters.WIDTH
import io.ktor.http.Parameters

class ImageAttributeAdapter {
    fun fromParameters(parameters: Parameters): RequestedImageAttributes {
        val returnFormat = AssetReturnFormat.fromQueryParam(parameters[RETURN])
        if (returnFormat == AssetReturnFormat.METADATA) {
            throw IllegalArgumentException("Cannot specify image attributes when requesting asset metadata")
        }

        return RequestedImageAttributes(
            width = parameters[WIDTH]?.toIntOrNull(),
            height = parameters[HEIGHT]?.toIntOrNull(),
            mimeType = parameters[MIME_TYPE],
        )
    }
}
