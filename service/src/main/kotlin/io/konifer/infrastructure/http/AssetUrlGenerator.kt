package io.konifer.infrastructure.http

import io.konifer.application.usecase.fetch.DeliveryUrl
import io.konifer.domain.asset.AssetData
import io.konifer.domain.context.HttpRequest
import io.konifer.domain.context.RequestContextFactory.Companion.PATH_NAMESPACE_SEPARATOR
import io.konifer.domain.path.DeliveryStrategy
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.path.TemplateProperties
import io.konifer.domain.path.TemplateProperties.Factory.TEMPLATE_BUCKET
import io.konifer.domain.path.TemplateProperties.Factory.TEMPLATE_KEY
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PresignedUrl
import io.konifer.infrastructure.HttpProperties
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import kotlinx.datetime.toKotlinLocalDateTime

class AssetUrlGenerator(
    private val httpProperties: HttpProperties,
    private val objectStore: ObjectStore,
) {
    companion object {
        const val ASSETS_API_PREFIX = "assets"
        const val ENTRY_PATH_SEGMENT = "entry"
    }

    /**
     * Generate a URL for an asset with an entry modifier. This URL is an absolute reference to the asset metadata.
     */
    fun generateAbsoluteLocationUrl(
        path: String,
        entryId: Long,
        origin: RequestConnectionPoint,
    ): Url =
        URLBuilder(resolveBaseUrl(origin))
            .apply {
                appendPathSegments(
                    ASSETS_API_PREFIX,
                    path.removePrefix("/"),
                    PATH_NAMESPACE_SEPARATOR,
                    ENTRY_PATH_SEGMENT,
                    entryId.toString(),
                )
            }.build()

    suspend fun generateDeliveryUrl(
        assetData: AssetData,
        request: HttpRequest,
        pathConfiguration: PathConfiguration,
    ): DeliveryUrl {
        val variant = assetData.variants.first()

        return when (pathConfiguration.deliveryProperties.strategy) {
            DeliveryStrategy.SERVICE -> generateAbsoluteContentUrl(assetData, request).toDeliveryUrl()
            DeliveryStrategy.PRESIGNED -> {
                when (
                    val presigned =
                        objectStore.generatePresignedUrl(
                            bucket = variant.objectStoreBucket,
                            key = variant.objectStoreKey,
                            ttl = pathConfiguration.deliveryProperties.preSigned.ttl,
                        )
                ) {
                    is PresignedUrl.Supported -> presigned.toDeliveryUrl()
                    PresignedUrl.NotSupported -> generateAbsoluteContentUrl(assetData, request).toDeliveryUrl()
                }
            }

            DeliveryStrategy.TEMPLATE -> {
                resolve(
                    templateProperties = pathConfiguration.deliveryProperties.template,
                    bucket = variant.objectStoreBucket,
                    key = variant.objectStoreKey,
                ).toDeliveryUrl()
            }
        }
    }

    private fun generateAbsoluteContentUrl(
        assetData: AssetData,
        request: HttpRequest,
    ): Url =
        URLBuilder(resolveBaseUrl(request.origin))
            .apply {
                appendPathSegments(
                    ASSETS_API_PREFIX,
                    assetData.path.removePrefix("/"),
                    PATH_NAMESPACE_SEPARATOR,
                    ENTRY_PATH_SEGMENT,
                    assetData.entryId.toString(),
                    "content",
                )
                this.parameters.appendAll(request.parameters)
            }.build()

    private fun resolve(
        templateProperties: TemplateProperties,
        bucket: String,
        key: String,
    ): Url =
        templateProperties.string
            .replace(TEMPLATE_BUCKET, bucket)
            .replace(TEMPLATE_KEY, key)
            .let(::Url)

    private fun resolveBaseUrl(origin: RequestConnectionPoint): Url =
        httpProperties.publicUrl
            ?: URLBuilder()
                .apply {
                    protocol = URLProtocol.createOrDefault(origin.scheme)
                    host = origin.serverHost
                    port = origin.serverPort
                }.build()

    private fun Url.toDeliveryUrl() = DeliveryUrl(url = this, expiresAt = null)

    private fun PresignedUrl.Supported.toDeliveryUrl() = DeliveryUrl(url = url, expiresAt = expiresAt?.toKotlinLocalDateTime())
}
