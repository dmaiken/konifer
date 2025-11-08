package io.asset.http

class AssetUrlGenerator(private val port: Int) {
    /**
     * Generate a URL for an asset with an entry modifier. This URL is an absolute reference to the asset metadata.
     */
    fun generateEntryMetadataUrl(
        host: String,
        path: String,
        entryId: Long,
    ): String {
        return "http://$host:$port/assets$path/-/entry/$entryId"
    }
}
