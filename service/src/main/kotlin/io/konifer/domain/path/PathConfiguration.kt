package io.konifer.domain.path

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ImageProperties
import io.konifer.domain.variant.preprocessing.PreProcessingProperties
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PathConfiguration(
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.ALLOWED_CONTENT_TYPES)
    val allowedContentTypes: List<String>? = null,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.PREPROCESSING)
    val preProcessing: PreProcessingProperties = PreProcessingProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.IMAGE)
    val image: ImageProperties = ImageProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.EAGER_VARIANTS)
    val eagerVariants: List<String> = emptyList(),
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.OBJECT_STORE)
    val objectStore: ObjectStoreProperties = ObjectStoreProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.RETURN_FORMAT)
    val returnFormat: ReturnFormatProperties = ReturnFormatProperties.default,
    @SerialName(ConfigurationPropertyKeys.PathPropertyKeys.CACHE_CONTROL)
    val cacheControl: CacheControlProperties = CacheControlProperties.default,
) {
    init {
        validate()
    }

    companion object {
        val default =
            PathConfiguration(
                allowedContentTypes = null,
                preProcessing = PreProcessingProperties.default,
                image = ImageProperties.default,
                eagerVariants = emptyList(),
                objectStore = ObjectStoreProperties.default,
                returnFormat = ReturnFormatProperties.default,
                cacheControl = CacheControlProperties.default,
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
