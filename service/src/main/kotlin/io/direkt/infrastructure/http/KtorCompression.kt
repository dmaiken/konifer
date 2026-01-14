package io.direkt.infrastructure.http

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.excludeContentType
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.identity
import io.ktor.server.plugins.compression.minimumSize

fun Application.configureCompression() {
    install(Compression) {
        gzip()
        deflate()
        identity()

        // Standard exclusions only exclude PNG and JPEG - we want all image types
        // We also want PDFs excluded
        // Also we don't handle video or audio but let's be safe here
        excludeContentType(
            ContentType.Video.Any,
            ContentType.Image.Any,
            ContentType.Audio.Any,
            ContentType.MultiPart.Any,
            ContentType.Application.Pdf,
        )

        minimumSize(1024)
    }
}
