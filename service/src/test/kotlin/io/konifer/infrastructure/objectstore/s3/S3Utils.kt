package io.konifer.infrastructure.objectstore.s3

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.CreateBucketRequest

fun createImageBuckets(
    s3Client: S3AsyncClient,
    vararg buckets: String,
) = runBlocking {
    buckets
        .map {
            launch {
                s3Client.createBucket(
                    CreateBucketRequest
                        .builder()
                        .bucket(it)
                        .build(),
                )
            }
        }.joinAll()
}
