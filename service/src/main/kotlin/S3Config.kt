package io

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val logger = KtorSimpleLogger("image.S3Config")

fun s3Client(properties: LocalstackProperties? = null): S3Client =
    S3Client {
        properties?.let {
            logger.info("Using LocalStack properties: $properties")
            credentialsProvider =
                StaticCredentialsProvider(
                    Credentials(
                        it.accessKey, it.secretKey,
                    ),
                )
            endpointUrl = Url.parse(properties.endpointUrl)
            region = it.region
        } ?: run {
            logger.info("Using standard AWS credential provider")
        }
    }

fun createImageBuckets(
    s3Client: S3Client,
    vararg buckets: String,
) = runBlocking {
    buckets.map {
        launch {
            s3Client.createBucket(
                CreateBucketRequest {
                    bucket = it
                },
            )
        }
    }.joinAll()
}

data class LocalstackProperties(
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val endpointUrl: String,
)
