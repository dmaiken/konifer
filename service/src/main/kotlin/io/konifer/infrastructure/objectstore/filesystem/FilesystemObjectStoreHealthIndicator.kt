package io.konifer.infrastructure.objectstore.filesystem

import io.konifer.infrastructure.health.CachingHealthIndicator
import io.konifer.infrastructure.health.HealthIndicator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths

class FilesystemObjectStoreHealthIndicator(
    scope: CoroutineScope,
    fileSystemProperties: FileSystemProperties,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HealthIndicator by CachingHealthIndicator(
        name = "filesystem object store",
        scope = scope,
        healthCheck = {
            withContext(ioDispatcher) {
                Paths.get(fileSystemProperties.mountPath).let {
                    Files.exists(it) && Files.isDirectory(it) && Files.isWritable(it)
                }
            }
        },
    )
