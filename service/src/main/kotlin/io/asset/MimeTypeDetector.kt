package io.asset

import org.apache.tika.Tika
import org.apache.tika.io.TikaInputStream

interface MimeTypeDetector {
    fun detect(byteArray: ByteArray): String
}

class TikaMimeTypeDetector : MimeTypeDetector {
    private val tika = Tika()

    override fun detect(byteArray: ByteArray): String {
        return tika.detect(TikaInputStream.get(byteArray))
    }
}
