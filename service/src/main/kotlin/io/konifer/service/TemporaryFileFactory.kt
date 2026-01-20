package io.konifer.service

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString

@OptIn(ExperimentalPathApi::class)
object TemporaryFileFactory {
    private val logger = KtorSimpleLogger("io.direkt.Application")

    private val tempDir: Path = Paths.get(System.getProperty("java.io.tmpdir"), ROOT_DIR)

    init {
        // Replace temporary directory with empty one
        logger.info("Deleting directory ${tempDir.pathString}")
        tempDir.deleteRecursively()

        logger.info("Creating directory ${tempDir.pathString}")
        tempDir.toFile().mkdirs()
    }

    const val ROOT_DIR = "direkt"
    const val UPLOAD_PREFIX = "asset-upload-"
    const val PRE_PROCESSED_PREFIX = "asset-pre-processed-"
    const val ORIGINAL_VARIANT_PREFIX = "original-variant-"
    const val PROCESSED_VARIANT_PREFIX = "processed-variant-"

    suspend fun createUploadTempFile(extension: String): Path =
        withContext(Dispatchers.IO) {
            tempDir.resolve("$UPLOAD_PREFIX-${UUID.randomUUID()}$extension")
        }

    suspend fun createPreProcessedTempFile(extension: String): Path =
        withContext(Dispatchers.IO) {
            tempDir.resolve("$PRE_PROCESSED_PREFIX-${UUID.randomUUID()}$extension")
        }

    suspend fun createOriginalVariantTempFile(extension: String): Path =
        withContext(Dispatchers.IO) {
            tempDir.resolve("$ORIGINAL_VARIANT_PREFIX-${UUID.randomUUID()}$extension")
        }

    suspend fun createProcessedVariantTempFile(extension: String): Path =
        withContext(Dispatchers.IO) {
            tempDir.resolve("$PROCESSED_VARIANT_PREFIX-${UUID.randomUUID()}$extension")
        }
}
