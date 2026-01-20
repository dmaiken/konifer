package io.konifer.domain.ports

interface MimeTypeDetector {
    fun detect(byteArray: ByteArray): String
}
