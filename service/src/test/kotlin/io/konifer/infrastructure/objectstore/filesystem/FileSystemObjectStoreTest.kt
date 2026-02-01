package io.konifer.infrastructure.objectstore.filesystem

import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.objectstore.ObjectStoreTest
import io.konifer.infrastructure.objectstore.property.CdnProperties
import io.konifer.infrastructure.objectstore.property.ObjectStoreProperties
import io.konifer.infrastructure.objectstore.property.RedirectMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.absolutePathString

class FileSystemObjectStoreTest : ObjectStoreTest() {
    val mountPath: Path =
        Paths
            .get(System.getProperty("java.io.tmpdir"))
            .resolve("object-store-test")
            .resolve("mnt")

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

    @ParameterizedTest
    @EnumSource(RedirectMode::class)
    fun `no url is returned regardless of redirect mode`(redirectMode: RedirectMode) =
        runTest {
            store.generateObjectUrl(
                bucket = BUCKET_1,
                key = UUID.randomUUID().toString(),
                properties =
                    ObjectStoreProperties(
                        redirectMode = redirectMode,
                        cdn =
                            CdnProperties(
                                domain = "my.domain.com",
                            ),
                    ),
            ) shouldBe null
        }
}
