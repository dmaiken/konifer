package io.konifer.client.content

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array

actual fun readResourceBytes(path: String): ByteArray {
    val fs = js("eval('require')('fs')")
    val pathModule = js("eval('require')('path')")

    // Strip the leading slash so Node path resolution treats it as relative
    val cleanPath = path.removePrefix("/")

    val possiblePaths =
        arrayOf(
            // Next to the compiled JS
            pathModule.resolve(js("__dirname"), cleanPath),
            // At the root of the generated NPM test package
            pathModule.resolve(js("__dirname"), "../", cleanPath),
            // Hard fallback to your actual source directory
            pathModule.resolve(js("__dirname"), "../../../../../client/src/commonTest/resources", cleanPath),
        ).unsafeCast<Array<String>>()

    var targetPath: String? = null
    for (i in possiblePaths.indices) {
        if (fs.existsSync(possiblePaths[i]).unsafeCast<Boolean>()) {
            targetPath = possiblePaths[i]
            break
        }
    }

    if (targetPath == null) {
        throw IllegalArgumentException(
            "Could not find resource '$path'. \nNode looked in these directories:\n${possiblePaths.joinToString("\n")}",
        )
    }

    try {
        val buffer = fs.readFileSync(targetPath)
        val int8Array =
            Int8Array(
                buffer.buffer.unsafeCast<ArrayBuffer>(),
                buffer.byteOffset as Int,
                buffer.length as Int,
            )
        return int8Array.unsafeCast<ByteArray>()
    } catch (e: dynamic) {
        throw IllegalArgumentException("Found resource at $targetPath but failed to read it: $e")
    }
}
