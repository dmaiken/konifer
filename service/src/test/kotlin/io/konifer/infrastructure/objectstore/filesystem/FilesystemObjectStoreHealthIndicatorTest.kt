package io.konifer.infrastructure.objectstore.filesystem

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class FilesystemObjectStoreHealthIndicatorTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `is healthy when the mount path is a writable directory`() =
        runTest {
            val indicator =
                FilesystemObjectStoreHealthIndicator(
                    scope = backgroundScope,
                    fileSystemProperties = FileSystemProperties(tempDirectory.toString()),
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )

            runCurrent()

            indicator.isHealthy() shouldBe true
        }

    @Test
    fun `is unhealthy when the mount path is not a directory`() =
        runTest {
            val file = Files.createFile(tempDirectory.resolve("not-a-directory"))
            val indicator =
                FilesystemObjectStoreHealthIndicator(
                    scope = backgroundScope,
                    fileSystemProperties = FileSystemProperties(file.toString()),
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )

            runCurrent()

            indicator.isHealthy() shouldBe false
        }
}
