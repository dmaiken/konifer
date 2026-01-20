package io.konifer.infrastructure.objectstore.filesystem

import io.konifer.domain.ports.ObjectRepository
import io.konifer.infrastructure.objectstore.ObjectRepositoryTest
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class FileSystemObjectRepositoryTest : ObjectRepositoryTest() {
    val mountPath: Path =
        Paths
            .get(System.getProperty("java.io.tmpdir"))
            .resolve("object-store-test")
            .resolve("mnt")

    val httpPath = "https://localhost:9000"

    @BeforeEach
    fun beforeEach() {
        if (!Files.exists(mountPath.parent)) {
            Files.createDirectories(mountPath.parent)
        }

        if (!Files.exists(mountPath)) {
            Files.createDirectories(mountPath)
        }

        emptyDirectory(mountPath)
    }

    override fun createObjectStore(): ObjectRepository =
        FileSystemObjectRepository(
            properties =
                FileSystemProperties(
                    mountPath = mountPath.absolutePathString(),
                    httpPath = httpPath,
                ),
        )

    private fun emptyDirectory(path: Path) {
        Files.walk(path).use { walk ->
            walk
                .sorted(Comparator.reverseOrder())
                .filter { !it.equals(path) } // Skip the root directory itself
                .forEach { Files.delete(it) }
        }
    }
}
