package io.konifer.infrastructure.objectstore.s3

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.SizeConstant.MB
import java.net.URI

fun s3Client(properties: S3ClientProperties): S3AsyncClient {
    val builder =
        S3AsyncClient
            .crtBuilder()
            .region(Region.US_EAST_1)
            .minimumPartSizeInBytes(5 * MB)
            .targetThroughputInGbps(20.0)

    if (properties.accessKey != null && properties.secretKey != null) {
        builder.credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
            ),
        )
    }

    properties.endpointUrl?.also {
        builder.endpointOverride(URI.create(it))
    }

    properties.region?.also {
        builder.region(Region.of(it))
    }

    builder.forcePathStyle(properties.forcePathStyle)

    return builder.build()
}

fun s3Presigner(properties: S3ClientProperties): S3Presigner {
    val builder = S3Presigner.builder()

    properties.region?.also {
        builder.region(Region.of(it))
    }

    if (properties.accessKey != null && properties.secretKey != null) {
        builder.credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
            ),
        )
    }

    properties.endpointUrl?.also {
        builder.endpointOverride(URI.create(it))
    }

    return builder.build()
}

fun s3TransferManager(s3Client: S3AsyncClient): S3TransferManager =
    S3TransferManager
        .builder()
        .s3Client(s3Client)
        .build()
