package io.asset

import org.apache.tika.Tika

interface MimeTypeDetector {
    fun detect(byteArray: ByteArray): String
}

class TikaMimeTypeDetector : MimeTypeDetector {
    private val tika = Tika()

    override fun detect(byteArray: ByteArray): String {
        return tika.detect(byteArray)
    }
}
