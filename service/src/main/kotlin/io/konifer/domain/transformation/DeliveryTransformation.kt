package io.konifer.domain.transformation

/** Materializes defaults while retaining orientation and color-space intent for regeneration. */
fun RequestedTransformation.forDelivery(resolved: Transformation): RequestedTransformation =
    copy(
        width = resolved.width,
        height = resolved.height,
        format = resolved.format,
        blur = resolved.blur,
        quality = resolved.quality,
        pad = resolved.padding.amount,
        padColor =
            resolved.padding.color
                .takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "#", separator = "") { it.toString(16).padStart(2, '0') }
                ?: "#00000000",
        stripMetadata =
            resolved.metadata.strip
                .sortedBy { it.name }
                .joinToString(",") { it.name.lowercase() },
        pixelChannels = resolved.pixels?.channels,
        pixelLayout = resolved.pixels?.layout,
    )
