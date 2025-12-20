package io.direkt.infrastructure.datastore

enum class DataStoreProvider {
    IN_MEMORY,
    POSTGRES,
    ;

    companion object Factory {
        val default = IN_MEMORY

        fun fromConfig(value: String): DataStoreProvider =
            when (value.lowercase()) {
                "in-memory" -> IN_MEMORY
                "postgresql" -> POSTGRES
                else -> throw IllegalArgumentException("Invalid data store provider: '$value'")
            }
    }
}
