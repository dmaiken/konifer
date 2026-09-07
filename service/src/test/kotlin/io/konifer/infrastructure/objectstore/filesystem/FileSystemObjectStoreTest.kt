package io.konifer.infrastructure.objectstore.filesystem

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PresignedUrl
import io.konifer.infrastructure.objectstore.ObjectStoreTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.minutes

open class FileSystemObjectStoreTest : ObjectStoreTest() {
    val mountPath: Path =
        Paths
            .get(System.getProperty("java.io.tmpdir"))
            .resolve("object-store-test")
            .resolve("mnt")

    override fun beforeCreateObjectStore() {
        if (!Files.exists(mountPath.parent)) {
            Files.createDirectories(mountPath.parent)
        }

        if (!Files.exists(mountPath)) {
            Files.createDirectories(mountPath)
        }

        emptyDirectory(mountPath)
    }

    override fun createObjectStore(): ObjectStore =
        FileSystemObjectStore(
            storeProperties =
                FileSystemProperties(
                    mountPath = mountPath.absolutePathString(),
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

    @Test
    fun `presigned urls are not supported`() =
        runTest {
            store.generatePresignedUrl(
                bucket = BUCKET_1,
                key = UuidCreator.getRandomBasedFast().toString(),
                ttl = 30.minutes,
            ) shouldBe PresignedUrl.NotSupported
        }
}
