package asset.variant

import image.model.Attributes
import image.model.Transformation
import io.asset.variant.ImageVariantAttributes
import io.image.model.Fit
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.json.Json
import net.openhft.hashing.LongHashFunction

class VariantParameterGenerator {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private val xx3 = LongHashFunction.xx3()

    /**
     * Generates the image variant attributes for an image asset. Also generates a hash of the attributes
     * which is not cryptographically secure! Uniqueness is the objective, not a secure hash.
     *
     * @return the attributes as a json string and a key which is an xxh3 hash of the attributes
     */
    fun generateImageVariantTransformations(imageTransformation: Transformation): Pair<String, Long> {
        val transformations =
            Json.encodeToString(
                ImageVariantTransformations(
                    height = imageTransformation.height,
                    width = imageTransformation.width,
                    format = imageTransformation.format,
                    fit = imageTransformation.fit,
                ),
            )
        val key = generateTransformationKey(transformations)

        logger.info("Generated transformations: $transformations with key: $key")
        return Pair(transformations, key)
    }

    fun generateImageVariantTransformations(attributes: Attributes): Pair<String, Long> {
        val transformations =
            Json.encodeToString(
                ImageVariantTransformations(
                    height = attributes.height,
                    width = attributes.width,
                    format = attributes.format,
                    fit = Fit.default,
                ),
            )
        val key = generateTransformationKey(transformations)

        logger.info("Generated transformations: $transformations using attributes: $attributes with key: $key")
        return Pair(transformations, key)
    }

    fun generateImageVariantAttributes(imageAttributes: Attributes): String = Json.encodeToString(
        ImageVariantAttributes(
            height = imageAttributes.height,
            width = imageAttributes.width,
            format = imageAttributes.format,
        )
    )

    private fun generateTransformationKey(attributes: String): Long {
        return xx3.hashBytes(attributes.toByteArray(Charsets.UTF_8))
    }
}
