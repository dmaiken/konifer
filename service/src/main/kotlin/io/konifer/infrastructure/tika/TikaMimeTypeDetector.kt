package io.konifer.infrastructure.tika

import io.konifer.domain.ports.MimeTypeDetector
import org.apache.tika.Tika

class TikaMimeTypeDetector : MimeTypeDetector {
    private val tika = Tika()

    override fun detect(byteArray: ByteArray): String = tika.detect(byteArray)
}
