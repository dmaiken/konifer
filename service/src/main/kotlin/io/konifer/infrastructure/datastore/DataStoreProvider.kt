package io.konifer.infrastructure.datastore

enum class DataStoreProvider {
    IN_MEMORY,
    POSTGRES,
    ;

    companion object Factory {
        val default = POSTGRES

        fun fromConfig(value: String): DataStoreProvider =
            when (value.lowercase()) {
                "in-memory" -> IN_MEMORY
                "postgresql" -> POSTGRES
                else -> throw IllegalArgumentException("Invalid data store provider: '$value'")
            }
    }
}
