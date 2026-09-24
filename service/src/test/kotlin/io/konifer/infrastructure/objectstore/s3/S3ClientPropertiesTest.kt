package io.konifer.infrastructure.objectstore.s3

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class S3ClientPropertiesTest {
    @Test
    fun `can create valid AWS S3 properties`() {
        shouldNotThrowAny {
            S3ClientProperties(
                endpointUrl = null,
                accessKey = null,
                secretKey = null,
                region = "us-east-1",
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
            )
        }
    }

    @Test
    fun `if provider hint is Floci then region must be supplied`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                S3ClientProperties(
                    endpointUrl = "localhost",
                    accessKey = null,
                    secretKey = null,
                    region = null,
                    providerHint = S3Provider.FLOCI,
                )
            }
        exception.message shouldBe "If using Floci you must specify endpointUrl and region"
    }

    @Test
    fun `if provider hint is Floci then endpoint url must be supplied`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                S3ClientProperties(
                    endpointUrl = null,
                    accessKey = null,
                    secretKey = null,
                    region = "us-east-1",
                    providerHint = S3Provider.FLOCI,
                )
            }
        exception.message shouldBe "If using Floci you must specify endpointUrl and region"
    }
}
