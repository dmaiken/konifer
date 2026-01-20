package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.Transformation
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.json.Json

object VariantParameterGenerator {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    /**
     * Generates the image variant attributes for an image asset.
     *
     * @return the attributes as a json string
     */
    fun generateImageVariantTransformations(imageTransformation: Transformation): String {
        val transformations = Json.encodeToString(ImageVariantTransformation.from(imageTransformation))

        logger.info("Generated transformations: $transformations")
        return transformations
    }

    /**
     * Generate [ImageVariantTransformation] using [Attributes]. This should only be used when persisting
     * the original variant since there will be no [Transformation] to use. The attributes "represent" the transformation.
     */
    fun generateImageVariantTransformations(attributes: Attributes): String {
        val transformations =
            Json.encodeToString(
                ImageVariantTransformation.originalTransformation(attributes),
            )

        logger.info("Generated transformations: $transformations using attributes: $attributes")
        return transformations
    }

    fun generateImageVariantAttributes(imageAttributes: Attributes): String =
        Json.encodeToString(
            ImageVariantAttributes(
                width = imageAttributes.width,
                height = imageAttributes.height,
                format = imageAttributes.format,
            ),
        )
}
