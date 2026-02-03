package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.Transformation

/**
 * It is VERY IMPORTANT that the [postgresJson] serializer is used here. We do not want to serialize null values
 * or default values because, if a new field is ever added (and it will be), we need backwards-compatability
 * and that is done by not serializing default values.
 */
object VariantParameterGenerator {
    /**
     * Generates the image variant attributes for an image asset.
     *
     * @return the attributes as a json string
     */
    fun generateImageVariantTransformations(imageTransformation: Transformation): String {
        val transformations = postgresJson.encodeToString(ImageVariantTransformation.from(imageTransformation))

        return transformations
    }

    /**
     * Generate [ImageVariantTransformation] using [Attributes]. This should only be used when persisting
     * the original variant since there will be no [Transformation] to use. The attributes "represent" the transformation.
     */
    fun generateImageVariantTransformations(attributes: Attributes): String {
        val transformations =
            postgresJson.encodeToString(
                ImageVariantTransformation.originalTransformation(attributes),
            )

        return transformations
    }

    fun generateImageVariantAttributes(imageAttributes: Attributes): String =
        postgresJson.encodeToString(
            ImageVariantAttributes(
                width = imageAttributes.width,
                height = imageAttributes.height,
                format = imageAttributes.format,
            ),
        )
}
