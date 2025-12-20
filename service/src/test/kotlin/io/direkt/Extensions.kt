package io.direkt

import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.File
import java.nio.file.Files

suspend fun <T> Class<T>.getResourceAsFile(name: String): File =
    Files.createTempFile("temp", ".tmp").toFile().also {
        it.deleteOnExit()
        this.getResourceAsStream(name)!!.toByteReadChannel().copyAndClose(it.writeChannel())
    }
