package io.direkt.infrastructure.properties

object ConfigurationProperties {
    const val DATASTORE = "datastore"
    const val OBJECT_STORE = "object-store"
    const val PATH_CONFIGURATION = "path-configuration"
    const val SOURCE = "source"
    const val VARIANT_GENERATION = "variant-generation"

    object DatabaseConfigurationProperties {
        const val PROVIDER = "provider"
        const val POSTGRES = "postgresql"

        object PostgresConfigurationProperties {
            const val DATABASE = "database"
            const val HOST = "host"
            const val PASSWORD = "password"
            const val PORT = "port"
            const val USER = "user"
        }
    }

    object ObjectStoreConfigurationProperties {
        const val PROVIDER = "provider"
        const val S3 = "s3"

        object S3ConfigurationProperties {
            const val ACCESS_KEY = "access-key"
            const val ENDPOINT_URL = "endpoint-url"
            const val REGION = "region"
            const val SECRET_KEY = "secret-key"
            const val USE_PATH_STYLE = "use-path-style"
        }
    }

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

    object SourceConfigurationProperties {
        const val URL = "url"
        const val MULTIPART = "multipart"

        object UrlConfigurationProperties {
            const val ALLOWED_DOMAINS = "allowed-domains"
            const val MAX_BYTES = "max-bytes"
        }

        object MultipartConfigurationProperties {
            const val MAX_BYTES = "max-bytes"
        }
    }

    object VariantGenerationConfigurationProperties {
        const val QUEUE_SIZE = "queue-size"
        const val SYNCHRONOUS_PRIORITY = "synchronous-priority"
        const val WORKERS = "workers"
    }
}
