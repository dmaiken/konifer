package io.konifer.domain.path

import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ObjectStoreProperties(
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.ObjectStorePropertyKeys.BUCKET)
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
    }

    private fun validate() {
        require(bucketRegex.matches(bucket)) {
            "Bucket must be conform to S3 name requirements"
        }
    }
}
