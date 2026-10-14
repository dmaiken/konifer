package io.konifer.client.assets.fetch

import io.konifer.common.http.AssetContentHeaders
import io.konifer.common.http.AttributesResponse
import io.konifer.common.http.LQIPResponse
import io.konifer.common.http.PixelAttributesResponse
import io.konifer.common.image.Rotate
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.ktor.http.Headers
import kotlinx.serialization.SerializationException

fun Headers.decodeContentMetadata(delivery: ContentDelivery): AssetContentMetadata {
    if (delivery == ContentDelivery.FOLLOW_REDIRECT) return AssetContentMetadata()

    return AssetContentMetadata(
        attributes = decodeAttributes(),
        alt = this[AssetContentHeaders.ALT],
        lqip =
            LQIPResponse(blurhash = this[AssetContentHeaders.BLURHASH], thumbhash = this[AssetContentHeaders.THUMBHASH])
                .takeIf { it.blurhash != null || it.thumbhash != null },
    )
}

private fun Headers.decodeAttributes(): AttributesResponse? {
    if (AssetContentHeaders.attributeNames.none { contains(it) }) return null

    val rotate =
        Rotate.entries.firstOrNull { it != Rotate.AUTO && it.queryParameterValue == required(AssetContentHeaders.ROTATE) }
            ?: invalid(AssetContentHeaders.ROTATE)
    val horizontalFlip =
        required(AssetContentHeaders.HORIZONTAL_FLIP).toBooleanStrictOrNull() ?: invalid(AssetContentHeaders.HORIZONTAL_FLIP)
    val format = required(AssetContentHeaders.FORMAT)
    val pixels = decodePixels()
    if (format == "pixels" && pixels == null) invalid(AssetContentHeaders.PIXELS_CHANNELS)

    return AttributesResponse(
        width = requiredInt(AssetContentHeaders.WIDTH),
        height = requiredInt(AssetContentHeaders.HEIGHT),
        format = format,
        rotate = rotate,
        horizontalFlip = horizontalFlip,
        colorSpace = required(AssetContentHeaders.COLOR_SPACE),
        pageCount = optionalInt(AssetContentHeaders.PAGE_COUNT),
        loop = optionalInt(AssetContentHeaders.LOOP),
        pixels = pixels,
    )
}

private fun Headers.decodePixels(): PixelAttributesResponse? {
    val names = listOf(AssetContentHeaders.PIXELS_CHANNELS, AssetContentHeaders.PIXELS_LAYOUT, AssetContentHeaders.PIXELS_DATA_TYPE)
    if (names.none { contains(it) }) return null

    return PixelAttributesResponse(
        channels = required(AssetContentHeaders.PIXELS_CHANNELS),
        layout =
            PixelLayout.entries.firstOrNull { it.queryParameterValue == required(AssetContentHeaders.PIXELS_LAYOUT) }
                ?: invalid(AssetContentHeaders.PIXELS_LAYOUT),
        dataType =
            PixelDataType.entries.firstOrNull { it.queryParameterValue == required(AssetContentHeaders.PIXELS_DATA_TYPE) }
                ?: invalid(AssetContentHeaders.PIXELS_DATA_TYPE),
    )
}

private fun Headers.required(name: String): String = this[name]?.takeIf { it.isNotBlank() } ?: invalid(name)

private fun Headers.requiredInt(name: String): Int = required(name).toIntOrNull() ?: invalid(name)

private fun Headers.optionalInt(name: String): Int? = this[name]?.let { it.toIntOrNull() ?: invalid(name) }

private fun invalid(name: String): Nothing = throw SerializationException("Missing or invalid content header: $name")
