package io.konifer

import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient

@Testcontainers
open class BaseLocalstackTestContainersTest : BaseFunctionalTest() {
    companion object {
        @JvmStatic
        @Container
        protected val localstack: LocalStackContainer =
            LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14"))
                .withEnv("LOCALSTACK_DISABLE_CHECKSUM_VALIDATION", "1")
                .withServices("s3")
    }

    protected fun createLocalstackS3Client(): S3AsyncClient =
        S3AsyncClient
            .builder()
            .endpointOverride(localstack.endpoint)
            .region(Region.of(localstack.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey),
                ),
            ).forcePathStyle(true)
            .build()
}
