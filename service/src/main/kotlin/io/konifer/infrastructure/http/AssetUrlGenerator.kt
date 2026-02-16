package io.konifer.infrastructure.http

import io.konifer.infrastructure.HttpProperties
import io.konifer.service.context.RequestContextFactory.Companion.PATH_NAMESPACE_SEPARATOR
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

class AssetUrlGenerator(
    private val httpProperties: HttpProperties,
) {
    /**
     * Generate a URL for an asset with an entry modifier. This URL is an absolute reference to the asset metadata.
     */
    fun generateAbsoluteLocationUrl(
        path: String,
        entryId: Long,
    ): String =
        URLBuilder(httpProperties.publicUrl)
            .apply {
                appendPathSegments("assets", path.removePrefix("/"), PATH_NAMESPACE_SEPARATOR, "entry", entryId.toString())
            }.build()
            .toString()

    fun generateAbsoluteContentUrl(
        path: String,
        entryId: Long,
        parameters: Parameters,
    ): String =
        URLBuilder(httpProperties.publicUrl)
            .apply {
                appendPathSegments("assets", path.removePrefix("/"), PATH_NAMESPACE_SEPARATOR, "entry", entryId.toString(), "content")
                this.parameters.appendAll(parameters)
            }.build()
            .toString()
}
