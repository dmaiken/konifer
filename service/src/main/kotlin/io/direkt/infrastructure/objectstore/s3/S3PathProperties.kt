package io.direkt.infrastructure.objectstore.s3

import io.direkt.infrastructure.properties.ConfigurationProperties.PathConfigurationProperties.S3PropertyKeys.BUCKET
import io.direkt.infrastructure.properties.ValidatedProperties
import io.direkt.infrastructure.properties.validateAndCreate
import io.ktor.server.config.ApplicationConfig

class S3PathProperties private constructor(
    val bucket: String,
) : ValidatedProperties {
    companion object Factory {
        private const val DEFAULT_BUCKET = "assets"
        val DEFAULT = S3PathProperties(DEFAULT_BUCKET)

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
            parent: S3PathProperties?,
        ): S3PathProperties =
            create(
                bucket = applicationConfig?.propertyOrNull(BUCKET)?.getString() ?: parent?.bucket,
            )

        fun create(bucket: String?): S3PathProperties =
            validateAndCreate {
                S3PathProperties(bucket ?: DEFAULT_BUCKET)
            }
    }

    override fun validate() {
        require(bucketRegex.matches(bucket)) {
            "Bucket must be conform to S3 name requirements"
        }
    }
}
