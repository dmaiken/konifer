package io.konifer.infrastructure.http

import io.konifer.application.usecase.fetch.DeliveryUrl
import io.konifer.common.image.ManipulationParameters.VARIANT_PROFILE
import io.konifer.domain.context.HttpRequest
import io.konifer.domain.context.RequestContextFactory.Companion.PATH_NAMESPACE_SEPARATOR
import io.konifer.domain.path.DeliveryStrategy
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.path.TemplateProperties
import io.konifer.domain.path.TemplateProperties.Factory.TEMPLATE_BUCKET
import io.konifer.domain.path.TemplateProperties.Factory.TEMPLATE_KEY
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PresignedUrl
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.variant.OnDemandVariantMode
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import io.konifer.infrastructure.HttpProperties
import io.konifer.infrastructure.http.signature.UrlSigningProperties
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.encodedPath
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AssetUrlGenerator(
    private val httpProperties: HttpProperties,
    private val objectStore: ObjectStore,
    private val signing: UrlSigningProperties,
) {
    companion object {
        const val ASSETS_API_PREFIX = "assets"
        const val ENTRY_PATH_SEGMENT = "entry"
        private val deliveryJson =
            Json {
                encodeDefaults = true
                explicitNulls = false
            }
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
        path: String,
        entryId: Long,
        variant: VariantDeliveryProjection,
        transformation: RequestedTransformation,
        request: HttpRequest,
        pathConfiguration: PathConfiguration,
    ): DeliveryUrl =
        when (pathConfiguration.deliveryProperties.strategy) {
            DeliveryStrategy.SERVICE -> {
                generateAbsoluteContentUrl(
                    path = path,
                    entryId = entryId,
                    request = request,
                    transformation = transformation,
                    pathConfiguration = pathConfiguration,
                ).toDeliveryUrl()
            }

            DeliveryStrategy.PRESIGNED -> {
                when (
                    val presigned =
                        objectStore.generatePresignedUrl(
                            bucket = variant.objectStoreBucket,
                            key = variant.objectStoreKey,
                            ttl = pathConfiguration.deliveryProperties.preSigned.ttl,
                        )
                ) {
                    is PresignedUrl.Supported -> {
                        presigned.toDeliveryUrl()
                    }

                    PresignedUrl.NotSupported -> {
                        generateAbsoluteContentUrl(
                            path = path,
                            entryId = entryId,
                            request = request,
                            transformation = transformation,
                            pathConfiguration = pathConfiguration,
                        ).toDeliveryUrl()
                    }
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

    private fun generateAbsoluteContentUrl(
        path: String,
        entryId: Long,
        request: HttpRequest,
        transformation: RequestedTransformation,
        pathConfiguration: PathConfiguration,
    ): Url {
        val contentPath =
            URLBuilder()
                .apply {
                    encodedPath = "/"
                    appendPathSegments(
                        ASSETS_API_PREFIX,
                        path.removePrefix("/"),
                        PATH_NAMESPACE_SEPARATOR,
                        ENTRY_PATH_SEGMENT,
                        entryId.toString(),
                        "content",
                    )
                }.encodedPath
        return URLBuilder(resolveBaseUrl(request.origin))
            .apply {
                encodedPath = encodedPath.trimEnd('/') + contentPath
                parameters.clear()
                deliveryJson.encodeToJsonElement(transformation).jsonObject.toSortedMap().forEach { (name, value) ->
                    parameters.append(name, value.jsonPrimitive.content)
                }
                if (pathConfiguration.transform.onDemandVariant.mode == OnDemandVariantMode.PROFILE_ONLY) {
                    request.parameters[VARIANT_PROFILE]?.let { parameters.append(VARIANT_PROFILE, it) }
                }
                signing.signer?.let { parameters.append(signing.signatureParam, it.sign(contentPath, parameters.build())) }
            }.build()
    }

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
