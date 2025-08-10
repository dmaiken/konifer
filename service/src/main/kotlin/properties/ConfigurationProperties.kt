package io.properties

object ConfigurationProperties {
    const val PATH_CONFIGURATION = "path-configuration"

    object PathConfigurationProperties {
        const val IMAGE = "image"
        const val PATH = "path"
        const val ALLOWED_CONTENT_TYPES = "allowed-content-types"
        const val VARIANT_PROFILES = "variant-profiles"
        const val EAGER_VARIANTS = "eager-variants"
        const val S3 = "s3"

        object ImagePropertyKeys {
            const val PREPROCESSING = "preprocessing"
            const val LQIP = "lqip"

            object PreProcessingPropertyKeys {
                const val MAX_HEIGHT = "max-height"
                const val MAX_WIDTH = "max-width"
                const val IMAGE_FORMAT = "image-format"
            }
        }

        object VariantProfilePropertyKeys {
            const val NAME = "name"
        }

        object S3PropertyKeys {
            const val BUCKET = "bucket"
        }
    }
}
