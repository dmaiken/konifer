package io.direkt.infrastructure.objectstore.s3

import io.direkt.infrastructure.properties.validateAndCreate
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class S3ClientPropertiesTest {
    @Test
    fun `can create valid AWS S3 properties`() {
        shouldNotThrowAny {
            validateAndCreate {
                S3ClientProperties(
                    endpointUrl = null,
                    accessKey = null,
                    secretKey = null,
                    region = "us-east-1",
                    usePathStyleUrl = false,
                )
            }
        }
    }

    @Test
    fun `can create valid properties for S3-compatible endpoint`() {
        shouldNotThrowAny {
            validateAndCreate {
                S3ClientProperties(
                    endpointUrl = "https://12345678.r2.cloudflarestorage.com",
                    accessKey = "accessKey",
                    secretKey = "secretKey",
                    region = null,
                    usePathStyleUrl = false,
                )
            }
        }
    }

    @Test
    fun `region must be specified if endpoint url is not set`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                validateAndCreate {
                    S3ClientProperties(
                        endpointUrl = null,
                        accessKey = null,
                        secretKey = null,
                        region = null,
                        usePathStyleUrl = false,
                    )
                }
            }
        exception.message shouldBe "Region must not be null if endpoint url is not specified"
    }

    @Test
    fun `if provider hint is localstack then region must be supplied`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                validateAndCreate {
                    S3ClientProperties(
                        endpointUrl = "localhost",
                        accessKey = null,
                        secretKey = null,
                        region = null,
                        usePathStyleUrl = false,
                        providerHint = S3Provider.LOCALSTACK,
                    )
                }
            }
        exception.message shouldBe "If using localstack you must specify endpointUrl and region"
    }

    @Test
    fun `if provider hint is localstack then endpoint url must be supplied`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                validateAndCreate {
                    S3ClientProperties(
                        endpointUrl = null,
                        accessKey = null,
                        secretKey = null,
                        region = "us-east-1",
                        usePathStyleUrl = false,
                        providerHint = S3Provider.LOCALSTACK,
                    )
                }
            }
        exception.message shouldBe "If using localstack you must specify endpointUrl and region"
    }
}
