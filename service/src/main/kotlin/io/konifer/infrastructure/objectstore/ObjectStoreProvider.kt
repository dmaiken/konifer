package io.konifer.infrastructure.objectstore

enum class ObjectStoreProvider {
    IN_MEMORY,
    S3,
    FILESYSTEM,
    ;

    companion object Factory {
        val default = FILESYSTEM

        fun fromConfig(value: String): ObjectStoreProvider =
            when (value.lowercase()) {
                "in-memory" -> IN_MEMORY
                "s3" -> S3
                "filesystem" -> FILESYSTEM
                else -> throw IllegalArgumentException("Invalid object store provider: '$value'")
            }
    }
}
