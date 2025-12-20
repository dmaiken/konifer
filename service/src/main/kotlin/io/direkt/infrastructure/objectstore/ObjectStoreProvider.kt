package io.direkt.infrastructure.objectstore

enum class ObjectStoreProvider {
    IN_MEMORY,
    S3,
    ;

    companion object Factory {
        val default = IN_MEMORY

        fun fromConfig(value: String): ObjectStoreProvider =
            when (value.lowercase()) {
                "in-memory" -> IN_MEMORY
                "s3" -> S3
                else -> throw IllegalArgumentException("Invalid object store provider: '$value'")
            }
    }
}
