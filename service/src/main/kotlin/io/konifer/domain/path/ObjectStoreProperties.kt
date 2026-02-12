package io.konifer.domain.path

import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString

data class ObjectStoreProperties(
    val bucket: String = DEFAULT_BUCKET,
) {
    init {
        validate()
    }

    companion object Factory {
        /**
         * Reflects rules outlined here: https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucketnamingrules.html
         */
        private val bucketRegex =
            Regex(
                "^((?!xn--)(?!amzn-s3-demo-)(?!sthree-)(?!.*-s3alias$)(?!.*--ol-s3$)(?!.*--x-s3$)(?!.*--table-s3$)" +
                    "(?!.*\\.mwrap$)[a-z0-9][a-z0-9-]{1,61}[a-z0-9])$",
            )
        private const val DEFAULT_BUCKET = "assets"
        val default = ObjectStoreProperties()

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: ObjectStoreProperties?,
        ): ObjectStoreProperties =
            ObjectStoreProperties(
                bucket =
                    applicationConfig?.tryGetString(ConfigurationPropertyKeys.PathPropertyKeys.ObjectStorePropertyKeys.BUCKET)
                        ?: parent?.bucket
                        ?: DEFAULT_BUCKET,
            )
    }

    private fun validate() {
        require(bucketRegex.matches(bucket)) {
            "Bucket must be conform to S3 name requirements"
        }
    }
}
