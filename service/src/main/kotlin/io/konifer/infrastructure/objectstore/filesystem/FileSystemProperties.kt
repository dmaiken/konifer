package io.konifer.infrastructure.objectstore.filesystem

import java.nio.file.Files
import java.nio.file.Paths

data class FileSystemProperties(
    val mountPath: String,
) {
    init {
        Paths.get(mountPath).also {
            require(Files.exists(it)) {
                "public-path does not exist: $it"
            }
        }
    }
}
