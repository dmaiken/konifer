package io.konifer

import io.konifer.infrastructure.TemporaryFileFactory
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi

abstract class BaseFunctionalTest {
    /**
     * We do this so we isolate the failing test in the case of lingering files. Otherwise, every
     * test after the failing test will fail due to lingering files.
     */
    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun cleanUpLingeringTempFiles() {
        Files
            .walk(TemporaryFileFactory.tempDir)
            .filter { Files.isRegularFile(it) }
            .forEach { Files.delete(it) }
    }

    @AfterEach
    fun assertNoTemporaryFilesRemain() {
        val lingeringFiles =
            Files
                .walk(TemporaryFileFactory.tempDir)
                .filter { Files.isRegularFile(it) }
                .toList()

        lingeringFiles.shouldBeEmpty()
    }
}
