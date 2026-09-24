package io.konifer

import io.floci.testcontainers.FlociContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.net.URI

@Testcontainers
open class BaseFlociTestContainersTest : BaseFunctionalTest() {
    companion object {
        @JvmStatic
        @Container
        protected val floci = FlociContainer("floci/floci:latest")
    }

    protected fun createFlociS3Client(): S3AsyncClient =
        S3AsyncClient
            .builder()
            .endpointOverride(URI.create(floci.endpoint))
            .region(Region.of(floci.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(floci.accessKey, floci.secretKey),
                ),
            ).forcePathStyle(true)
            .build()
}
