package io.konifer.infrastructure.objectstore.s3

import com.github.f4b6a3.uuid.UuidCreator
import io.floci.testcontainers.FlociContainer
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PresignedUrl
import io.konifer.infrastructure.objectstore.ObjectStoreTest
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.transfer.s3.S3TransferManager
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import kotlin.time.Duration.Companion.days

@Testcontainers
class S3ObjectStoreTest : ObjectStoreTest() {
    companion object {
        @JvmStatic
        @Container
        private val floci = FlociContainer("floci/floci:latest")
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
    inner class GeneratePresignedUrlTests {
        @Test
        fun `can create a presigned URL`() =
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

                val presignedUrl = store.generatePresignedUrl(bucket, key, 7.days)
                presignedUrl.shouldBeInstanceOf<PresignedUrl.Supported>()
                URI.create(presignedUrl.url.toString()).toURL().apply {
                    query shouldContain "X-Amz-Algorithm"
                    query shouldContain "X-Amz-Credential"
                }
            }

        @Test
        fun `presigned url has expiresAt`() =
            runTest {
                val now = LocalDateTime.now(UTC)
                val s3Clients = createS3Client()
                val store =
                    S3ObjectStore(
                        s3Client = s3Clients.client,
                        s3TransferManager = s3Clients.transferManager,
                        s3Presigner = s3Clients.presigner,
                    )
                val bucket = "bucket"
                val key = UuidCreator.getRandomBasedFast().toString()

                val presignedUrl = store.generatePresignedUrl(bucket, key, ttl = 7.days)
                presignedUrl.shouldBeInstanceOf<PresignedUrl.Supported>()
                presignedUrl.expiresAt shouldNotBe null
                presignedUrl.expiresAt!! shouldBeAfter now
            }
    }

    private fun createS3Client(): S3Clients {
        val properties =
            S3ClientProperties(
                endpointUrl = floci.endpoint,
                region = floci.region,
                accessKey = floci.accessKey,
                secretKey = floci.secretKey,
                forcePathStyle = true,
                providerHint = S3Provider.FLOCI,
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
