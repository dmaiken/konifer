package io.properties

interface ValidatedProperties {
    /**
     * Validates the properties in the implementation.
     *
     * @throws IllegalArgumentException if the properties are invalid.
     */
    fun validate()
}

inline fun <T : ValidatedProperties> validateAndCreate(factory: () -> T): T {
    val instance = factory()
    instance.validate()
    return instance
}
