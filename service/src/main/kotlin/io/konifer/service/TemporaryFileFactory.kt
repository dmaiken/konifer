package io.konifer.service

import com.github.f4b6a3.uuid.UuidCreator
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString

@OptIn(ExperimentalPathApi::class)
object TemporaryFileFactory {
    private val logger = KtorSimpleLogger("io.konifer.Application")

    private val tempDir: Path = Paths.get(System.getProperty("java.io.tmpdir"), ROOT_DIR)

    init {
        // Replace temporary directory with empty one
        logger.info("Deleting directory if exists ${tempDir.pathString}")
        tempDir.deleteRecursively()

        logger.info("Creating directory if exists ${tempDir.pathString}")
        tempDir.toFile().mkdirs()
    }

    const val ROOT_DIR = "konifer"
    const val UPLOAD_PREFIX = "asset-upload-"
    const val PRE_PROCESSED_PREFIX = "asset-pre-processed-"
    const val ORIGINAL_VARIANT_PREFIX = "original-variant-"
    const val PROCESSED_VARIANT_PREFIX = "processed-variant-"

    suspend fun createUploadTempFile(extension: String): Path =
        withContext(Dispatchers.IO) {
            tempDir.resolve("$UPLOAD_PREFIX-${UuidCreator.getRandomBasedFast()}$extension")
        }

    suspend fun createPreProcessedTempFile(extension: String): Path =
        withContext(Dispatchers.IO) {
            tempDir.resolve("$PRE_PROCESSED_PREFIX-${UuidCreator.getRandomBasedFast()}$extension")
        }

    suspend fun createOriginalVariantTempFile(extension: String): Path =
        withContext(Dispatchers.IO) {
            tempDir.resolve("$ORIGINAL_VARIANT_PREFIX-${UuidCreator.getRandomBasedFast()}$extension")
        }

    suspend fun createProcessedVariantTempFile(extension: String): Path =
        withContext(Dispatchers.IO) {
            tempDir.resolve("$PROCESSED_VARIANT_PREFIX-${UuidCreator.getRandomBasedFast()}$extension")
        }
}
