package io.konifer.infrastructure.datastore.postgres.statement

data class AssetDataToInclude(
    val onlyReady: Boolean,
    val includeLabels: Boolean,
    val includeTags: Boolean,
) {
    companion object {
        val allFieldsOnlyReady: AssetDataToInclude =
            AssetDataToInclude(
                onlyReady = true,
                includeLabels = true,
                includeTags = true,
            )
    }
}
