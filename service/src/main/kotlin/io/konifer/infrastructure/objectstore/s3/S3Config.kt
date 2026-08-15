package io.konifer.infrastructure.objectstore.s3

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.transfer.s3.S3TransferManager
import java.net.URI

fun s3Client(properties: S3ClientProperties): S3AsyncClient {
    val builder =
        S3AsyncClient
            .builder()
            .multipartEnabled(true)
            .forcePathStyle(properties.forcePathStyle)

    properties.region?.let {
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

fun s3Presigner(properties: S3ClientProperties): S3Presigner {
    val builder =
        S3Presigner
            .builder()
            .serviceConfiguration(
                S3Configuration
                    .builder()
                    .pathStyleAccessEnabled(properties.forcePathStyle)
                    .build(),
            )

    if (properties.accessKey != null && properties.secretKey != null) {
        builder.credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
            ),
        )
    }

    properties.region?.let {
        builder.region(Region.of(it))
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
