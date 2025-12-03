package io.direkt.asset

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

object TemporaryFileFactory {
    const val TEMP_SUFFIX = ".tmp"
    const val UPLOAD_PREFIX = "asset-upload-"
    const val PRE_PROCESSED_PREFIX = "asset-pre-processed-"

    suspend fun createUploadTempFile(): File =
        withContext(Dispatchers.IO) {
            Files.createTempFile(UPLOAD_PREFIX, TEMP_SUFFIX).toFile().apply {
                deleteOnExit()
            }
        }

    suspend fun createPreProcessedTempFile(): File =
        withContext(Dispatchers.IO) {
            Files.createTempFile(PRE_PROCESSED_PREFIX, TEMP_SUFFIX).toFile().apply {
                deleteOnExit()
            }
        }
}
