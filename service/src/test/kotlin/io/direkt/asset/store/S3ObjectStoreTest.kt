package io.direkt.asset.store

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.direkt.asset.variant.AssetVariant
import io.direkt.createImageBuckets
import io.direkt.s3.S3ClientProperties
import io.direkt.s3.S3ObjectStore
import io.image.model.Attributes
import io.image.model.ImageFormat
import io.image.model.LQIPs
import io.image.model.Transformation
import io.kotest.matchers.shouldBe
import io.properties.validateAndCreate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.LocalDateTime
import java.util.UUID

@Testcontainers
class S3ObjectStoreTest : ObjectStoreTest() {
    companion object {
        @JvmStatic
        @Container
        private val localstack =
            LocalStackContainer(DockerImageName.parse("localstack/localstack:s3-latest"))
                .withEnv("LOCALSTACK_DISABLE_CHECKSUM_VALIDATION", "1") // Localstack does not like performing a checksum
                .withServices(LocalStackContainer.Service.S3)

        @JvmStatic
        @BeforeAll
        fun startContainer() {
            localstack.start()
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            localstack.stop()
        }
    }

    lateinit var s3Client: S3Client

    override fun createObjectStore(): ObjectStore {
        s3Client =
            S3Client {
                credentialsProvider =
                    StaticCredentialsProvider(
                        Credentials(
                            localstack.accessKey,
                            localstack.secretKey,
                        ),
                    )
                endpointUrl = Url.parse(localstack.endpoint.toString())
                region = localstack.region
            }
        createImageBuckets(s3Client, BUCKET_1, BUCKET_2, BUCKET_3)
        // Create bucket for test
        runBlocking {
            s3Client.createBucket(
                CreateBucketRequest {
                    bucket = "something"
                },
            )
            s3Client.createBucket(
                CreateBucketRequest {
                    bucket = "somethingelse"
                },
            )
        }
        return S3ObjectStore(
            s3Client = s3Client,
            s3ClientProperties =
                validateAndCreate {
                    S3ClientProperties(
                        endpointUrl = "localhost.localstack.cloud:${localstack.firstMappedPort}",
                        region = localstack.region,
                        accessKey = null,
                        secretKey = null,
                        usePathStyleUrl = false,
                    )
                },
        )
    }

    @Nested
    inner class GenerateS3ObjectUrlTests {
        @Test
        fun `generates a path-style AWS URL if specified`() {
            val store =
                S3ObjectStore(
                    s3Client = s3Client,
                    s3ClientProperties =
                        validateAndCreate {
                            S3ClientProperties(
                                endpointUrl = null,
                                region = "us-east-1",
                                accessKey = null,
                                secretKey = null,
                                usePathStyleUrl = true,
                            )
                        },
                )
            val variant = createVariant()

            store.generateObjectUrl(variant) shouldBe
                "https://s3.us-east-1.amazonaws.com/${variant.objectStoreBucket}/${variant.objectStoreKey}"
        }

        @Test
        fun `generates a non-AWS path-style URL if specified`() {
            val store =
                S3ObjectStore(
                    s3Client = s3Client,
                    s3ClientProperties =
                        validateAndCreate {
                            S3ClientProperties(
                                endpointUrl = "minio.local",
                                region = null,
                                accessKey = null,
                                secretKey = null,
                                usePathStyleUrl = true,
                            )
                        },
                )
            val variant = createVariant()

            store.generateObjectUrl(variant) shouldBe "https://minio.local/${variant.objectStoreBucket}/${variant.objectStoreKey}"
        }

        @Test
        fun `generates a virtual-style AWS URL if specified`() {
            val store =
                S3ObjectStore(
                    s3Client = s3Client,
                    s3ClientProperties =
                        validateAndCreate {
                            S3ClientProperties(
                                endpointUrl = null,
                                region = "us-east-1",
                                accessKey = null,
                                secretKey = null,
                                usePathStyleUrl = false,
                            )
                        },
                )
            val variant = createVariant()

            store.generateObjectUrl(variant) shouldBe
                "https://${variant.objectStoreBucket}.s3.us-east-1.amazonaws.com/${variant.objectStoreKey}"
        }

        @Test
        fun `generates a non-AWS virtual-style URL if specified`() {
            val store =
                S3ObjectStore(
                    s3Client = s3Client,
                    s3ClientProperties =
                        validateAndCreate {
                            S3ClientProperties(
                                endpointUrl = "minio.local",
                                region = null,
                                accessKey = null,
                                secretKey = null,
                                usePathStyleUrl = false,
                            )
                        },
                )
            val variant = createVariant()

            store.generateObjectUrl(variant) shouldBe "https://${variant.objectStoreBucket}.minio.local/${variant.objectStoreKey}"
        }

        private fun createVariant(): AssetVariant =
            AssetVariant(
                objectStoreBucket = "assets",
                objectStoreKey = UUID.randomUUID().toString(),
                isOriginalVariant = true,
                attributes =
                    Attributes(
                        width = 100,
                        height = 100,
                        format = ImageFormat.PNG,
                    ),
                transformation = Transformation.ORIGINAL_VARIANT,
                transformationKey = 1234L,
                lqip = LQIPs.NONE,
                createdAt = LocalDateTime.now(),
            )
    }
}
