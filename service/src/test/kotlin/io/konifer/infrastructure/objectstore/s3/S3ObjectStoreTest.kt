package io.konifer.infrastructure.objectstore.s3

import aws.sdk.kotlin.services.s3.S3Client
import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.domain.path.PreSignedProperties
import io.konifer.domain.path.RedirectProperties
import io.konifer.domain.path.RedirectStrategy
import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.objectstore.ObjectStoreTest
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
        val (s3Client, _) = createS3Client()
        return S3ObjectStore(
            s3Client = s3Client,
        )
    }

    @Nested
    inner class GenerateS3ObjectUrlTests {
        @Test
        fun `can create presignedUrl`() =
            runTest {
                val (s3Client, _) = createS3Client()
                val store =
                    S3ObjectStore(s3Client)
                val bucket = "bucket"
                val key = UuidCreator.getRandomBasedFast().toString()

                val properties =
                    RedirectProperties(
                        strategy = RedirectStrategy.PRESIGNED,
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

    private fun createS3Client(): Pair<S3Client, S3ClientProperties> {
        val properties =
            S3ClientProperties(
                endpointUrl = localstack.endpoint.toString(),
                region = localstack.region,
                accessKey = localstack.accessKey,
                secretKey = localstack.secretKey,
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
