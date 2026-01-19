package io.direkt.infrastructure.objectstore.filesystem

import java.nio.file.Files
import java.nio.file.Paths

data class FileSystemProperties(
    val mountPath: String,
    val httpPath: String,
) {
    init {
        require(httpPath.startsWith("https://") || httpPath.startsWith("http://")) {
            "Public path must be valid: $httpPath"
        }

        Paths.get(mountPath).also {
            require(Files.exists(it)) {
                "public-path does not exist: $it"
            }
        }
    }
}
