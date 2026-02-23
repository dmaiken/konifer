package io.konifer.infrastructure.objectstore.s3

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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.transfer.s3.S3TransferManager
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
                .withServices("s3")

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
        val s3Clients = createS3Client()
        createImageBuckets(s3Clients.client, BUCKET_1, BUCKET_2)
        return S3ObjectStore(
            s3Client = s3Clients.client,
            s3TransferManager = s3Clients.transferManager,
            s3Presigner = s3Clients.presigner,
        )
    }

    @Nested
    inner class GenerateS3ObjectUrlTests {
        @Test
        fun `can create presignedUrl`() =
            runTest {
                val s3Clients = createS3Client()
                val store =
                    S3ObjectStore(
                        s3Client = s3Clients.client,
                        s3TransferManager = s3Clients.transferManager,
                        s3Presigner = s3Clients.presigner,
                    )
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
                    query shouldContain "X-Amz-Algorithm"
                    query shouldContain "X-Amz-Credential"
                }
            }
    }

    private fun createS3Client(): S3Clients {
        val properties =
            S3ClientProperties(
                endpointUrl = localstack.endpoint.toString(),
                region = localstack.region,
                accessKey = localstack.accessKey,
                secretKey = localstack.secretKey,
                providerHint = S3Provider.LOCALSTACK,
            )

        val client = s3Client(properties)
        return S3Clients(
            client = client,
            transferManager = s3TransferManager(client),
            presigner = s3Presigner(properties),
            properties = properties,
        )
    }
}

data class S3Clients(
    val client: S3AsyncClient,
    val transferManager: S3TransferManager,
    val presigner: S3Presigner,
    val properties: S3ClientProperties,
)
