package io.aws

import io.ktor.server.config.ApplicationConfig
import io.properties.ConfigurationProperties.PathConfigurationProperties.S3PropertyKeys.BUCKET
import io.properties.ValidatedProperties
import io.properties.validateAndCreate

class S3Properties private constructor(
    val bucket: String,
) : ValidatedProperties {
    companion object Factory {
        private const val DEFAULT_BUCKET = "assets"
        val DEFAULT = S3Properties(DEFAULT_BUCKET)

        /**
         * Reflects rules outlined here: https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucketnamingrules.html
         */
        private val bucketRegex =
            Regex(
                "^((?!xn--)(?!amzn-s3-demo-)(?!sthree-)(?!.*-s3alias$)(?!.*--ol-s3$)(?!.*--x-s3$)(?!.*--table-s3$)" +
                    "(?!.*\\.mwrap$)[a-z0-9][a-z0-9-]{1,61}[a-z0-9])$",
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: S3Properties?,
        ): S3Properties =
            create(
                bucket = applicationConfig?.propertyOrNull(BUCKET)?.getString() ?: parent?.bucket,
            )

        fun create(bucket: String?): S3Properties =
            validateAndCreate {
                S3Properties(bucket ?: DEFAULT_BUCKET)
            }
    }

    override fun validate() {
        require(bucketRegex.matches(bucket)) {
            "Bucket must be conform to S3 name requirements"
        }
    }
}
