package io.konifer.infrastructure.objectstore.s3

import aws.sdk.kotlin.services.s3.S3Client
import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.objectstore.ObjectStoreTest
import io.konifer.infrastructure.objectstore.property.ObjectStoreProperties
import io.konifer.infrastructure.objectstore.property.PreSignedProperties
import io.konifer.infrastructure.objectstore.property.RedirectMode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.util.UUID
import kotlin.time.Duration.Companion.days

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

    override fun createObjectStore(): ObjectStore {
        val (s3Client, properties) = createS3Client()
        return S3ObjectStore(
            s3Client = s3Client,
            s3ClientProperties = properties,
        )
    }

    @Nested
    inner class GenerateS3ObjectUrlTests {
        @Test
        fun `generates a path-style AWS URL if specified`() =
            runTest {
                val (s3Client, properties) = createS3Client(usePathStyleUrl = true)
                val store =
                    S3ObjectStore(
                        s3Client = s3Client,
                        s3ClientProperties = properties.copy(endpointUrl = null, providerHint = null),
                    )
                val bucket = "bucket"
                val key = UUID.randomUUID().toString()
                val pathProperties =
                    ObjectStoreProperties(
                        redirectMode = RedirectMode.BUCKET,
                    )

                store.generateObjectUrl(bucket, key, pathProperties) shouldBe
                    "https://s3.us-east-1.amazonaws.com/$bucket/$key"
            }

        @Test
        fun `generates a non-AWS path-style URL if specified`() =
            runTest {
                val (s3Client, properties) = createS3Client(usePathStyleUrl = true)
                val store =
                    S3ObjectStore(
                        s3Client = s3Client,
                        s3ClientProperties = properties.copy(endpointUrl = "minio.local"),
                    )
                val bucket = "bucket"
                val key = UUID.randomUUID().toString()

                val pathProperties =
                    ObjectStoreProperties(
                        redirectMode = RedirectMode.BUCKET,
                    )
                store.generateObjectUrl(bucket, key, pathProperties) shouldBe
                    "https://minio.local/$bucket/$key"
            }

        @Test
        fun `generates a virtual-host AWS URL if specified`() =
            runTest {
                val (s3Client, _) = createS3Client(usePathStyleUrl = false)
                val store =
                    S3ObjectStore(
                        s3Client = s3Client,
                        s3ClientProperties =
                            S3ClientProperties(
                                endpointUrl = null,
                                region = "us-east-1",
                                accessKey = null,
                                secretKey = null,
                                usePathStyleUrl = false,
                            ),
                    )
                val bucket = "bucket"
                val key = UUID.randomUUID().toString()

                val pathProperties =
                    ObjectStoreProperties(
                        redirectMode = RedirectMode.BUCKET,
                    )
                store.generateObjectUrl(bucket, key, pathProperties) shouldBe
                    "https://$bucket.s3.us-east-1.amazonaws.com/$key"
            }

        @Test
        fun `generates a non-AWS virtual-host URL if specified`() =
            runTest {
                val (s3Client, _) = createS3Client(usePathStyleUrl = false)
                val store =
                    S3ObjectStore(
                        s3Client = s3Client,
                        s3ClientProperties =
                            S3ClientProperties(
                                endpointUrl = "minio.local",
                                region = null,
                                accessKey = null,
                                secretKey = null,
                                usePathStyleUrl = false,
                            ),
                    )
                val bucket = "bucket"
                val key = UUID.randomUUID().toString()

                val pathProperties =
                    ObjectStoreProperties(
                        redirectMode = RedirectMode.BUCKET,
                    )
                store.generateObjectUrl(bucket, key, pathProperties) shouldBe
                    "https://$bucket.minio.local/$key"
            }

        @Test
        fun `can create presignedUrl`() =
            runTest {
                val (s3Client, _) = createS3Client(usePathStyleUrl = false)
                val store =
                    S3ObjectStore(
                        s3Client = s3Client,
                        s3ClientProperties =
                            S3ClientProperties(
                                endpointUrl = null,
                                region = "us-east-1",
                                accessKey = null,
                                secretKey = null,
                                usePathStyleUrl = false,
                            ),
                    )
                val bucket = "bucket"
                val key = UUID.randomUUID().toString()

                val properties =
                    ObjectStoreProperties(
                        redirectMode = RedirectMode.PRESIGNED,
                        preSigned =
                            PreSignedProperties(
                                ttl = 7.days,
                            ),
                    )
                val url = store.generateObjectUrl(bucket, key, properties)
                url shouldNotBe null
                URI.create(url!!).toURL().apply {
                    query shouldContain "x-id"
                    query shouldContain "X-Amz-Algorithm"
                    query shouldContain "X-Amz-Credential"
                }
            }
    }

    private fun createS3Client(usePathStyleUrl: Boolean = false): Pair<S3Client, S3ClientProperties> {
        val properties =
            S3ClientProperties(
                endpointUrl = localstack.endpoint.toString(),
                region = localstack.region,
                accessKey = localstack.accessKey,
                secretKey = localstack.secretKey,
                usePathStyleUrl = usePathStyleUrl,
                providerHint = S3Provider.LOCALSTACK,
            )

        return Pair(
            first =
                s3Client(properties).also {
                    createImageBuckets(it, BUCKET_1, BUCKET_2, BUCKET_3)
                },
            second = properties,
        )
    }
}
