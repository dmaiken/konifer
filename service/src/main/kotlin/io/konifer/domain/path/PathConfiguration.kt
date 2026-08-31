package io.konifer.domain.path

import io.konifer.common.image.ImageFormat
import io.konifer.domain.asset.AssetLimitProperties
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.rules.upload.UploadRuleset
import io.konifer.domain.variant.TransformProperties
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PathConfiguration(
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.ALLOWED_CONTENT_TYPES)
    val allowedContentTypes: List<String>? = null,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.TRANSFORM)
    val transform: TransformProperties = TransformProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.LQIP)
    val lqip: Set<LQIPImplementation> = emptySet(),
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.LIMITS)
    val limits: AssetLimitProperties = AssetLimitProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.OBJECT_STORE)
    val objectStore: ObjectStoreProperties = ObjectStoreProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.RETURN_FORMAT)
    val returnFormat: ReturnFormatProperties = ReturnFormatProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.CACHE_CONTROL)
    val cacheControl: CacheControlProperties = CacheControlProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.UPLOAD_RULESET)
    val uploadRuleset: UploadRuleset = UploadRuleset.default,
) {
    init {
        validate()
    }

    companion object {
        val default =
            PathConfiguration(
                allowedContentTypes = null,
                objectStore = ObjectStoreProperties.default,
                returnFormat = ReturnFormatProperties.default,
                cacheControl = CacheControlProperties.default,
                transform = TransformProperties.default,
            )
    }

    private fun validate() {
        allowedContentTypes?.let { allowedContentTypes ->
            val supportedContentTypes = ImageFormat.entries.map { it.mimeType }
            allowedContentTypes.forEach { allowedContentType ->
                require(supportedContentTypes.contains(allowedContentType)) {
                    "$allowedContentType is not a supported content type"
                }
            }
        }
    }
}
