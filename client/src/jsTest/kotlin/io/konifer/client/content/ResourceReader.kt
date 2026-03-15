package io.konifer.client.content

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array

actual fun readResourceBytes(path: String): ByteArray {
    val fs = js("require('fs')")
    val relativePath = "src/commonTest/resources$path"

    try {
        val buffer = fs.readFileSync(relativePath)

        // Create a JS Int8Array view over the Node Buffer's memory
        val int8Array =
            Int8Array(
                buffer.buffer.unsafeCast<ArrayBuffer>(),
                buffer.byteOffset as Int,
                buffer.length as Int,
            )

        // Zero-copy cast it directly to a Kotlin ByteArray
        return int8Array.unsafeCast<ByteArray>()
    } catch (e: dynamic) {
        throw IllegalArgumentException("Could not find resource at $relativePath: $e")
    }
}
