package io.direkt.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object TemporaryFileFactory {
    const val UPLOAD_PREFIX = "asset-upload-"
    const val PRE_PROCESSED_PREFIX = "asset-pre-processed-"
    const val ORIGINAL_VARIANT_PREFIX = "original-variant-"
    const val PROCESSED_VARIANT_PREFIX = "processed-variant-"

    suspend fun createUploadTempFile(extension: String): File =
        withContext(Dispatchers.IO) {
            Files.createTempFile(UPLOAD_PREFIX, extension).toFile()
        }

    fun createPreProcessedTempFile(extension: String): File =
        Files.createTempFile(PRE_PROCESSED_PREFIX, extension).toFile()

    fun createOriginalVariantTempFile(extension: String): File =
        Files.createTempFile(ORIGINAL_VARIANT_PREFIX, extension).toFile()

    suspend fun createProcessedVariantTempFile(extension: String): File =
        withContext(Dispatchers.IO) {
            Files.createTempFile(PROCESSED_VARIANT_PREFIX, extension).toFile()
        }
}
