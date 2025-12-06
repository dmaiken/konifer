package io.direkt.infrastructure.objectstore.s3

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.util.logging.KtorSimpleLogger

private val logger = KtorSimpleLogger("io.direkt.infrastructure.s3.S3Config")

fun s3Client(properties: S3ClientProperties): S3Client =
    S3Client {
        logger.info("Using s3 client properties: $properties")
        if (properties.accessKey != null && properties.secretKey != null) {
            credentialsProvider =
                StaticCredentialsProvider(
                    Credentials(
                        properties.accessKey,
                        properties.secretKey,
                    ),
                )
        }
        properties.endpointUrl?.also {
            endpointUrl = Url.parse(it)
        }
        properties.region?.also {
            region = it
        }
    }

