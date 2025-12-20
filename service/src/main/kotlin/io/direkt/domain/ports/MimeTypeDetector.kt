package io.direkt.domain.ports

interface MimeTypeDetector {
    fun detect(byteArray: ByteArray): String
}
