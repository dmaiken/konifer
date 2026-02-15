package io.konifer.infrastructure.property

object ConfigurationPropertyKeys {
    const val DATASTORE = "data-store"
    const val OBJECT_STORE = "object-store"
    const val PATH_CONFIGURATION = "paths"
    const val SOURCE = "source"
    const val VARIANT_GENERATION = "variant-generation"
    const val URL_SIGNING = "url-signing"
    const val HTTP = "http"
    const val VARIANT_PROFILES = "variant-profiles"

    object DataStorePropertyKeys {
        const val PROVIDER = "provider"
        const val POSTGRES = "postgresql"

        object PostgresPropertyKeys {
            const val DATABASE = "database"
            const val HOST = "host"
            const val PASSWORD = "password"
            const val PORT = "port"
            const val USER = "user"
            const val SSL_MODE = "ssl-mode"
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
            const val FORCE_PATH_STYLE = "force-path-style"
        }

        object FileSystemPropertyKeys {
            const val MOUNT_PATH = "mount-path"
        }
    }

    object SourceConfigurationPropertyKeys {
        const val URL = "url"
        const val MULTIPART = "multipart"

        object UrlConfigurationPropertyKeys {
            const val ALLOWED_DOMAINS = "allowed-domains"
            const val MAX_BYTES = "max-bytes"
        }

        object MultipartConfigurationPropertyKeys {
            const val MAX_BYTES = "max-bytes"
        }
    }

    object VariantGenerationConfigurationPropertyKeys {
        const val QUEUE_SIZE = "queue-size"
        const val SYNCHRONOUS_PRIORITY = "synchronous-priority"
        const val WORKERS = "workers"
    }

    object UrlSigningConfigurationPropertyKeys {
        const val ENABLED = "enabled"
        const val ALGORITHM = "algorithm"
        const val SECRET_KEY = "secret-key"
    }

    object HttpPropertyKeys {
        const val PUBLIC_URL = "public-url"
    }

    object PathPropertyKeys {
        const val IMAGE = "image"
        const val PATH = "path"
        const val ALLOWED_CONTENT_TYPES = "allowed-content-types"
        const val EAGER_VARIANTS = "eager-variants"
        const val OBJECT_STORE = "object-store"
        const val RETURN_FORMAT = "return-format"
        const val PREPROCESSING = "preprocessing"
        const val CACHE_CONTROL = "cache-control"

        object ImagePropertyKeys {
            const val LQIP = "lqip"

            object PreProcessingPropertyKeys {
                const val ENABLED = "enabled"
                const val IMAGE = "image"

                object ImagePreProcessingPropertyKeys {
                    const val MAX_HEIGHT = "max-height"
                    const val MAX_WIDTH = "max-width"
                }
            }
        }

        object VariantProfilePropertyKeys {
            const val NAME = "name"
        }

        object ObjectStorePropertyKeys {
            const val BUCKET = "bucket"

            object RedirectPropertyKeys {
                const val STRATEGY = "strategy"
                const val PRESIGNED = "presigned"
                const val TEMPLATE = "template"

                object PreSignedPropertyKeys {
                    const val TTL = "ttl"
                }

                object TemplatePropertyKeys {
                    const val STRING = "string"
                }
            }
        }

        object ReturnFormatPropertyKeys {
            const val REDIRECT = "redirect"
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
}
