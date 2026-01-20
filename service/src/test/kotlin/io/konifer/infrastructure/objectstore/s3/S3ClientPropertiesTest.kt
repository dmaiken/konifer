package io.konifer.infrastructure.objectstore.s3

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class S3ClientPropertiesTest {
    @Test
    fun `can create valid AWS S3 properties`() {
        shouldNotThrowAny {
            S3ClientProperties(
                endpointUrl = null,
                accessKey = null,
                secretKey = null,
                region = "us-east-1",
                usePathStyleUrl = false,
                presignedUrlProperties = null,
            )
        }
    }

    @Test
    fun `can create valid properties for S3-compatible endpoint`() {
        shouldNotThrowAny {
            S3ClientProperties(
                endpointUrl = "https://12345678.r2.cloudflarestorage.com",
                accessKey = "accessKey",
                secretKey = "secretKey",
                region = null,
                usePathStyleUrl = false,
                presignedUrlProperties = null,
            )
        }
    }

    @Test
    fun `region must be specified if endpoint url is not set`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                S3ClientProperties(
                    endpointUrl = null,
                    accessKey = null,
                    secretKey = null,
                    region = null,
                    usePathStyleUrl = false,
                    presignedUrlProperties = null,
                )
            }
        exception.message shouldBe "Region must not be null if endpoint url is not specified"
    }

    @Test
    fun `if provider hint is localstack then region must be supplied`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                S3ClientProperties(
                    endpointUrl = "localhost",
                    accessKey = null,
                    secretKey = null,
                    region = null,
                    usePathStyleUrl = false,
                    providerHint = S3Provider.LOCALSTACK,
                    presignedUrlProperties = null,
                )
            }
        exception.message shouldBe "If using localstack you must specify endpointUrl and region"
    }

    @Test
    fun `if provider hint is localstack then endpoint url must be supplied`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                S3ClientProperties(
                    endpointUrl = null,
                    accessKey = null,
                    secretKey = null,
                    region = "us-east-1",
                    usePathStyleUrl = false,
                    providerHint = S3Provider.LOCALSTACK,
                    presignedUrlProperties = null,
                )
            }
        exception.message shouldBe "If using localstack you must specify endpointUrl and region"
    }

    @Test
    fun `presigned url ttl cannot be negative`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                PresignedUrlProperties(
                    ttl = (-1).minutes,
                )
            }
        exception.message shouldBe "Presigned TTL must be positive"
    }

    @Test
    fun `presigned url ttl cannot be greater than 7 days`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                PresignedUrlProperties(
                    ttl = 7.days.plus(1.seconds),
                )
            }
        exception.message shouldBe "Presigned TTL cannot be greater than 7 days"
    }
}
