package io.konifer.infrastructure.objectstore.s3

import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.PathConfigurationRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@OptIn(ExperimentalCoroutinesApi::class)
class S3ObjectStoreHealthIndicatorTest {
    private val pathConfigurationRepository = mockk<PathConfigurationRepository>()
    private val s3Client = mockk<S3AsyncClient>()

    @Test
    fun `is healthy when the default bucket is accessible`() =
        runTest {
            val request = slot<Consumer<HeadBucketRequest.Builder>>()
            every { pathConfigurationRepository.fetch("/") } returns PathConfiguration.default
            every { s3Client.headBucket(capture(request)) } returns
                CompletableFuture.completedFuture(HeadBucketResponse.builder().build())
            val indicator =
                S3ObjectStoreHealthIndicator(
                    scope = backgroundScope,
                    pathConfigurationRepository = pathConfigurationRepository,
                    s3Client = s3Client,
                )

            runCurrent()

            indicator.isHealthy() shouldBe true
            HeadBucketRequest
                .builder()
                .also(request.captured::accept)
                .build()
                .bucket() shouldBe "assets"
        }

    @Test
    fun `is unhealthy when the default bucket is inaccessible`() =
        runTest {
            every { pathConfigurationRepository.fetch("/") } returns PathConfiguration.default
            every { s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>()) } returns
                CompletableFuture.failedFuture(IllegalStateException("unavailable"))
            val indicator =
                S3ObjectStoreHealthIndicator(
                    scope = backgroundScope,
                    pathConfigurationRepository = pathConfigurationRepository,
                    s3Client = s3Client,
                )

            runCurrent()

            indicator.isHealthy() shouldBe false
        }
}
