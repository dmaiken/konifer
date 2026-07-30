package io.konifer.client.asset.content

actual fun readResourceBytes(path: String): ByteArray {
    // We use an anonymous object to get a reference to the classloader
    val stream =
        object {}.javaClass.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Could not find resource at $path")

    return stream.use { it.readBytes() }
}
