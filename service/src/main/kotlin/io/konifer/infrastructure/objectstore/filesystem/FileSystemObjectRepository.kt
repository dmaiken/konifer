package io.konifer.infrastructure.objectstore.filesystem

import io.konifer.domain.ports.FetchResult
import io.konifer.domain.ports.ObjectRepository
import io.ktor.util.cio.readChannel
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime

class FileSystemObjectRepository(
    private val properties: FileSystemProperties,
) : ObjectRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override suspend fun persist(
        bucket: String,
        key: String,
        file: File,
    ): LocalDateTime =
        withContext(Dispatchers.IO) {
            val target = resolvePath(bucket, key)

            if (!Files.exists(target.parent)) {
                Files.createDirectories(target.parent)
            }
            Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING)

            LocalDateTime.now()
        }

    override suspend fun fetch(
        bucket: String,
        key: String,
        channel: ByteWriteChannel,
    ): FetchResult {
        val path = resolvePath(bucket, key)
        val file = path.toFile()
        val exists = withContext(Dispatchers.IO) { file.exists() }

        if (!exists) {
            logger.info("File with name: $key in directory: $bucket does not exist")
            return FetchResult.NOT_FOUND.also {
                channel.flushAndClose()
            }
        }

        val totalBytes = file.readChannel().copyAndClose(channel)

        return FetchResult.found(
            contentLength = totalBytes,
        )
    }

    override suspend fun exists(
        bucket: String,
        key: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val path = resolvePath(bucket, key)
                Files.exists(path)
            } catch (_: SecurityException) {
                false
            }
        }

    override suspend fun delete(
        bucket: String,
        key: String,
    ): Unit =
        withContext(Dispatchers.IO) {
            try {
                val path = resolvePath(bucket, key)
                Files.deleteIfExists(path)
            } catch (_: SecurityException) {
                // Ignore path traversal attempts during delete
            }
        }

    override suspend fun deleteAll(
        bucket: String,
        keys: List<String>,
    ) = withContext(Dispatchers.IO) {
        keys.forEach { key ->
            try {
                val path = resolvePath(bucket, key)
                Files.deleteIfExists(path)
            } catch (_: SecurityException) {
                // Skip invalid paths
            }
        }
    }

    override suspend fun generateObjectUrl(
        bucket: String,
        key: String,
    ): String = "${properties.httpPath}/$bucket/$key"

    private fun resolvePath(
        bucket: String,
        key: String,
    ): Path {
        val resolved =
            Paths
                .get(properties.mountPath)
                .resolve(bucket)
                .resolve(key)
                .normalize()
                .toAbsolutePath()

        // The resulting path must start with the rootPath to prevent any shenanigans
        if (!resolved.startsWith(properties.mountPath)) {
            throw SecurityException("Invalid path: Access outside of mount path denied.")
        }

        return resolved
    }
}
