package io.konifer.infrastructure.objectstore.s3

import io.konifer.domain.ports.AssetSourceForbiddenException
import io.konifer.domain.ports.AssetSourceTimeoutException
import io.konifer.domain.ports.AssetSourceUnavailableException
import io.konifer.domain.ports.InvalidAssetSourceException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException
import software.amazon.awssdk.core.exception.ApiCallTimeoutException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

@Testcontainers
class AwsS3SourceReaderTest {
    companion object {
        private const val BUCKET = "source-assets"

        @JvmStatic
        @Container
        private val localstack =
            LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14"))
                .withEnv("LOCALSTACK_DISABLE_CHECKSUM_VALIDATION", "1")
                .withServices("s3")

        private lateinit var localstackClient: S3AsyncClient
        private lateinit var reader: AwsS3SourceReader

        @JvmStatic
        @BeforeAll
        fun createReader() {
            localstackClient =
                s3Client(
                    S3ClientProperties(
                        endpointUrl = localstack.endpoint.toString(),
                        region = localstack.region,
                        accessKey = localstack.accessKey,
                        secretKey = localstack.secretKey,
                        forcePathStyle = true,
                        providerHint = S3Provider.LOCALSTACK,
                    ),
                )
            localstackClient
                .createBucket(CreateBucketRequest.builder().bucket(BUCKET).build())
                .join()
            reader = AwsS3SourceReader(lazy { localstackClient })
        }

        @JvmStatic
        @AfterAll
        fun closeClient() {
            localstackClient.close()
        }
    }

    @Test
    fun `fetches an object from Amazon S3`() =
        runTest {
            val key = "nested/source.txt"
            val expected = "content fetched through the source reader".encodeToByteArray()
            localstackClient
                .putObject(
                    PutObjectRequest
                        .builder()
                        .bucket(BUCKET)
                        .key(key)
                        .build(),
                    AsyncRequestBody.fromBytes(expected),
                ).join()
            val channel = ByteChannel(autoFlush = true)
            val content = async { channel.toByteArray() }

            val result = reader.fetch(bucket = BUCKET, key = key, channel = channel)

            result.found shouldBe true
            result.contentLength shouldBe expected.size.toLong()
            content.await() shouldBe expected
        }

    @Test
    fun `returns not found when the object does not exist`() =
        runTest {
            val channel = ByteChannel(autoFlush = true)

            val result = reader.fetch(bucket = BUCKET, key = "missing.txt", channel = channel)

            result.found shouldBe false
            result.contentLength shouldBe 0
            channel.toByteArray().shouldBeEmpty()
        }
}

class AwsS3SourceReaderFailureTest {
    @Test
    fun `translates an API call timeout`() =
        runTest {
            val cause = ApiCallTimeoutException.create(1)

            val exception = fetchWithClientFailure<AssetSourceTimeoutException>(cause)

            exception.cause shouldBe cause
        }

    @Test
    fun `translates an API call attempt timeout`() =
        runTest {
            val cause = ApiCallAttemptTimeoutException.create(1)

            val exception = fetchWithClientFailure<AssetSourceTimeoutException>(cause)

            exception.cause shouldBe cause
        }

    @Test
    fun `translates an S3 forbidden response`() =
        runTest {
            val cause = s3Exception(statusCode = 403)

            val exception = fetchWithClientFailure<AssetSourceForbiddenException>(cause)

            exception.message shouldBe "Amazon S3 denied access to the asset source"
        }

    @Test
    fun `translates another S3 client error`() =
        runTest {
            val cause = s3Exception(statusCode = 400)

            val exception = fetchWithClientFailure<InvalidAssetSourceException>(cause)

            exception.message shouldBe "Amazon S3 rejected the asset source"
            exception.cause shouldBe cause
        }

    @Test
    fun `translates an S3 server error`() =
        runTest {
            val cause = s3Exception(statusCode = 500)

            val exception = fetchWithClientFailure<AssetSourceUnavailableException>(cause)

            exception.message shouldBe "Amazon S3 could not retrieve the asset source"
            exception.cause shouldBe cause
        }

    @Test
    fun `translates an SDK client failure`() =
        runTest {
            val cause = SdkClientException.create("No credentials available")

            val exception = fetchWithClientFailure<AssetSourceUnavailableException>(cause)

            exception.message shouldBe "Amazon S3 source is unavailable"
            exception.cause shouldBe cause
        }

    private suspend inline fun <reified T : Throwable> fetchWithClientFailure(cause: Throwable): T {
        val reader =
            AwsS3SourceReader(
                s3Client = lazy { throw cause },
            )

        return shouldThrow<T> {
            reader.fetch(
                bucket = "source-assets",
                key = "source.txt",
                channel = ByteChannel(),
            )
        }
    }

    private fun s3Exception(statusCode: Int): S3Exception {
        val builder = S3Exception.builder()
        builder.statusCode(statusCode)
        builder.message("S3 request failed")
        return builder.build() as S3Exception
    }
}
