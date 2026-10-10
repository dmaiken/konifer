package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.transformation.Transformation
import io.konifer.domain.variant.attribute.Attributes

/**
 * It is VERY IMPORTANT that the [postgresJson] serializer is used here. We do not want to serialize null values
 * or default values because, if a new field is ever added (and it will be), we need backwards-compatability
 * and that is done by not serializing default values.
 */
object VariantParameterGenerator {
    /**
     * Generates the image variant attributes for an image asset.
     *
     * @return the attributes as a JSON string
     */
    fun generateImageVariantTransformations(transformation: Transformation): String =
        postgresJson.encodeToString(ImageVariantTransformation.from(transformation))

    /**
     * Generate [ImageVariantTransformation] using [Attributes]. This should only be used when persisting
     * the original variant since there will be no [Transformation] to use. The attributes "represent" the transformation.
     */
    fun generateImageVariantTransformations(attributes: Attributes): String =
        postgresJson.encodeToString(
            ImageVariantTransformation.originalTransformation(attributes),
        )

    fun generateImageVariantAttributes(attributes: Attributes): String =
        postgresJson.encodeToString(
            ImageVariantAttributes.from(attributes),
        )
}
