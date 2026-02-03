package io.konifer.infrastructure.objectstore.property

import io.konifer.infrastructure.properties.ConfigurationPropertyKeys
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import kotlin.time.Duration.Companion.days

data class ObjectStoreProperties(
    val bucket: String = DEFAULT_BUCKET,
    val redirectMode: RedirectMode = RedirectMode.default,
    val preSigned: PreSignedProperties = PreSignedProperties.default,
    val cdn: CdnProperties = CdnProperties.default,
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
                redirectMode =
                    applicationConfig
                        ?.tryGetString(ConfigurationPropertyKeys.PathPropertyKeys.ObjectStorePropertyKeys.REDIRECT_MODE)
                        ?.let { RedirectMode.fromConfig(it) }
                        ?: parent?.redirectMode
                        ?: RedirectMode.default,
                preSigned =
                    PreSignedProperties.create(
                        applicationConfig =
                            applicationConfig?.tryGetConfig(
                                ConfigurationPropertyKeys.PathPropertyKeys.ObjectStorePropertyKeys.PRESIGNED,
                            ),
                        parent = parent?.preSigned,
                    ),
                cdn =
                    CdnProperties.create(
                        applicationConfig =
                            applicationConfig?.tryGetConfig(
                                ConfigurationPropertyKeys.PathPropertyKeys.ObjectStorePropertyKeys.CDN,
                            ),
                        parent = parent?.cdn,
                    ),
            )
    }

    private fun validate() {
        require(bucketRegex.matches(bucket)) {
            "Bucket must be conform to S3 name requirements"
        }
        if (redirectMode == RedirectMode.PRESIGNED) {
            require(preSigned.ttl.isPositive()) {
                "Presigned TTL must be positive"
            }
            require(preSigned.ttl <= 7.days) {
                "Presigned TTL cannot be greater than 7 days"
            }
        }
        if (redirectMode == RedirectMode.CDN) {
            require(cdn.domain?.isNotBlank() == true) {
                "CDN domain must be populated cannot be blank"
            }
        }
    }
}
