package io.konifer.infrastructure.objectstore.property

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.server.config.HoconApplicationConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ObjectStorePropertiesTest {
    @Test
    fun `can create S3Properties`() {
        val properties =
            ObjectStoreProperties(
                bucket = "test-bucket",
            )

        properties.bucket shouldBe "test-bucket"
    }

    @Test
    fun `can create S3Properties with defaults`() {
        val properties = ObjectStoreProperties()

        properties.bucket shouldBe "assets"
    }

    @Test
    fun `can create S3Properties with parent`() {
        val parent =
            ObjectStoreProperties(
                bucket = "test-bucket",
            )
        val properties =
            ObjectStoreProperties.create(
                applicationConfig = null,
                parent = parent,
            )

        properties.bucket shouldBe parent.bucket
    }

    @Test
    fun `can create S3Properties with application config`() {
        val config =
            ConfigFactory.parseString(
                """
                bucket = my-bucket
                """.trimIndent(),
            )
        val properties =
            ObjectStoreProperties.create(
                applicationConfig = HoconApplicationConfig(config),
                parent = null,
            )

        properties.bucket shouldBe "my-bucket"
    }

    @Test
    fun `can create S3Properties with application config and parent`() {
        val parent =
            ObjectStoreProperties(
                bucket = "test-bucket",
            )
        val config =
            ConfigFactory.parseString(
                """
                bucket = my-bucket
                """.trimIndent(),
            )
        val properties =
            ObjectStoreProperties.create(
                applicationConfig = HoconApplicationConfig(config),
                parent = parent,
            )

        properties.bucket shouldBe "my-bucket"
    }

    /**
     * Examples derived from these rules: https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucketnamingrules.html
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "aa",
            "this-is-way-too-long-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "bucket,1",
            "bucket_1",
            "bucket-s3alias",
            "amzn-s3-demo-bucket",
            "sthree-bucket",
            "xn--bucket",
            "bucket--ol-s3",
            "bucket.mrap",
            "bucket--x-s3",
            "bucket--table-s3",
            "bucket.period",
            "bucket..period",
            "BUCKET",
            "-bucket-",
        ],
    )
    fun `bucket name conform to S3 standards`(bucket: String) {
        val exception =
            shouldThrow<IllegalArgumentException> {
                ObjectStoreProperties(
                    bucket = bucket,
                )
            }

        exception.message shouldBe "Bucket must be conform to S3 name requirements"
    }
}
