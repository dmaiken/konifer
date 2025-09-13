package asset.variant

import image.model.ImageAttributes
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
    fun generateImageVariantAttributes(imageAttributes: ImageAttributes): Pair<String, Long> {
        val attributes =
            Json.encodeToString(
                ImageVariantAttributes(
                    height = imageAttributes.height,
                    width = imageAttributes.width,
                    format = imageAttributes.format,
                ),
            )
        val key = generateAttributesKey(attributes)

        logger.info("Generated attributes: $attributes with key: $key")
        return Pair(attributes, key)
    }

    private fun generateAttributesKey(attributes: String): Long {
        return xx3.hashBytes(attributes.toByteArray(Charsets.UTF_8))
    }
}
