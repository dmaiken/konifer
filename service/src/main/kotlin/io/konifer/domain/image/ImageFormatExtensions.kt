package io.konifer.domain.image

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.ImageFormat.entries
import io.konifer.infrastructure.vips.VipsProperties

fun ImageFormat.Factory.fromFormat(string: String): ImageFormat =
    entries.firstOrNull {
        it.format == string
    } ?: throw IllegalArgumentException("Unsupported image format: $string")

fun ImageFormat.Factory.fromMimeType(string: String): ImageFormat =
    entries.firstOrNull {
        it.mimeType.equals(string, ignoreCase = true)
    } ?: throw IllegalArgumentException("Unsupported image mime type: $string")

fun ImageFormat.Factory.fromExtension(extension: String): ImageFormat =
    entries.firstOrNull {
        it.extension == extension
    } ?: throw IllegalArgumentException("Unsupported image extension: $extension")

val ImageFormat.vipsProperties: VipsProperties
    get() =
        when (this) {
            ImageFormat.JPEG ->
                VipsProperties(
                    supportsQuality = true,
                    // Sharp's default quality setting
                    defaultQuality = 80,
                    supportsAlpha = false,
                    supportsPaging = false,
                )
            ImageFormat.PNG ->
                VipsProperties(
                    supportsQuality = false,
                    // Not used since PNG does not support lossy compression
                    defaultQuality = 100,
                    supportsAlpha = true,
                    supportsPaging = false,
                )
            ImageFormat.WEBP ->
                VipsProperties(
                    supportsQuality = true,
                    // Sharp's default quality setting
                    defaultQuality = 80,
                    supportsAlpha = true,
                    supportsPaging = true,
                )
            ImageFormat.AVIF ->
                VipsProperties(
                    supportsQuality = true,
                    // Sharp's default quality setting
                    defaultQuality = 50,
                    supportsAlpha = true,
                    supportsPaging = false,
                )
            ImageFormat.JPEG_XL ->
                VipsProperties(
                    supportsQuality = true,
                    defaultQuality = 90,
                    supportsAlpha = true,
                    supportsPaging = false,
                )
            ImageFormat.HEIC ->
                VipsProperties(
                    supportsQuality = true,
                    defaultQuality = 50,
                    supportsAlpha = true,
                    supportsPaging = false,
                )
            ImageFormat.GIF ->
                VipsProperties(
                    supportsQuality = false,
                    defaultQuality = 100,
                    supportsAlpha = false,
                    supportsPaging = true,
                )
        }
