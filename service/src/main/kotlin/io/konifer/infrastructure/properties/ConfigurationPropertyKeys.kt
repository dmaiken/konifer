package io.konifer.infrastructure.properties

object ConfigurationPropertyKeys {
    const val DATASTORE = "datastore"
    const val OBJECT_STORE = "object-store"
    const val PATH_CONFIGURATION = "path-configuration"
    const val SOURCE = "source"
    const val VARIANT_GENERATION = "variant-generation"
    const val URL_SIGNING = "url-signing"

    object DatabasePropertyKeys {
        const val PROVIDER = "provider"
        const val POSTGRES = "postgresql"

        object PostgresPropertyKeys {
            const val DATABASE = "database"
            const val HOST = "host"
            const val PASSWORD = "password"
            const val PORT = "port"
            const val USER = "user"
        }
    }

    object ObjectRepositoryPropertyKeys {
        const val PROVIDER = "provider"
        const val S3 = "s3"
        const val FILESYSTEM = "filesystem"

        object S3PropertyKeys {
            const val ACCESS_KEY = "access-key"
            const val ENDPOINT_URL = "endpoint-url"
            const val REGION = "region"
            const val SECRET_KEY = "secret-key"
            const val USE_PATH_STYLE = "use-path-style"
            const val PRESIGN_URL = "presign"

            object PreSignedUrlPropertyKeys {
                const val ENABLED = "enabled"
                const val TTL = "ttl"
            }
        }

        object FileSystemPropertyKeys {
            const val MOUNT_PATH = "mount-path"
            const val HTTP_PATH = "http-path"
        }
    }

    object PathPropertyKeys {
        const val IMAGE = "image"
        const val PATH = "path"
        const val ALLOWED_CONTENT_TYPES = "allowed-content-types"
        const val VARIANT_PROFILES = "variant-profiles"
        const val EAGER_VARIANTS = "eager-variants"
        const val OBJECT_STORE = "object-store"
        const val PREPROCESSING = "preprocessing"
        const val CACHE_CONTROL = "cache-control"

        object ImagePropertyKeys {
            const val LQIP = "lqip"

            object PreProcessingPropertyKeys {
                const val IMAGE = "image"
                const val MAX_HEIGHT = "max-height"
                const val MAX_WIDTH = "max-width"
            }
        }

        object VariantProfilePropertyKeys {
            const val NAME = "name"
        }

        object S3PropertyKeys {
            const val BUCKET = "bucket"
        }

        object CacheControlPropertyKeys {
            const val ENABLED = "enabled"
            const val MAX_AGE = "max-age"
            const val SHARED_MAX_AGE = "s-maxage"
            const val VISIBILITY = "visibility"
            const val REVALIDATE = "revalidate"
            const val STALE_WHILE_REVALIDATE = "stale-while-revalidate"
            const val STALE_IF_ERROR = "stale-if-error"
            const val IMMUTABLE = "immutable"
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

    object UrlSigningConfigurationProperties {
        const val ENABLED = "enabled"
        const val ALGORITHM = "algorithm"
        const val SECRET_KEY = "secret-key"
    }
}
