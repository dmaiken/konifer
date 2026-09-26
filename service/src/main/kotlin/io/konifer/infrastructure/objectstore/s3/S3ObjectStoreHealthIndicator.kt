package io.konifer.infrastructure.objectstore.s3

import io.konifer.domain.ports.PathConfigurationRepository
import io.konifer.infrastructure.health.CachingHealthIndicator
import io.konifer.infrastructure.health.HealthIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.s3.S3AsyncClient

class S3ObjectStoreHealthIndicator(
    scope: CoroutineScope,
    pathConfigurationRepository: PathConfigurationRepository,
    s3Client: S3AsyncClient,
) : HealthIndicator by CachingHealthIndicator(
        name = "s3 object store",
        scope = scope,
        healthCheck = {
            val bucket =
                pathConfigurationRepository
                    .fetch("/")
                    .objectStore
                    .bucket

            s3Client.headBucket { it.bucket(bucket) }.await()
            true
        },
    )
