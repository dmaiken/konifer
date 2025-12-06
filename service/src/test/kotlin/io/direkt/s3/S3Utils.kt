package io.direkt.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun createImageBuckets(
    s3Client: S3Client,
    vararg buckets: String,
) = runBlocking {
    buckets
        .map {
            launch {
                s3Client.createBucket(
                    CreateBucketRequest {
                        bucket = it
                    },
                )
            }
        }.joinAll()
}