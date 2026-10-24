package io.konifer.infrastructure.datastore.postgres.projection

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.infrastructure.datastore.postgres.VariantParameterGenerator
import io.konifer.infrastructure.datastore.postgres.postgresJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import konifer.jooq.tables.records.AssetVariantRecord
import kotlinx.serialization.SerializationException
import org.jooq.JSONB
import org.junit.jupiter.api.Test

class PostgresVariantDeliveryProjectionTest {
    private val expectedAttributes =
        Attributes(
            width = 100.toDimension(),
            height = 80.toDimension(),
            format = ImageFormat.PNG,
            colorSpace = ColorSpace.SRGB,
        )
    private val expectedTransformation = Transformation.fromAttributes(expectedAttributes).copy(width = 50.toDimension())
    private val lqips = LQIPs("blurhash", "thumbhash")
    private val invalidJson = JSONB.valueOf("{")

    @Test
    fun `identity and storage fields do not decode any JSON`() {
        val record = record()
        val projection = PostgresVariantDeliveryProjection(record)

        projection.id shouldBe VariantId(checkNotNull(record.id))
        projection.objectStoreBucket shouldBe "bucket"
        projection.objectStoreKey shouldBe "variant.png"
        projection.isOriginalVariant shouldBe false
    }

    @Test
    fun `original specification does not decode stored transformation JSON`() {
        val projection = PostgresVariantDeliveryProjection(record().apply { originalVariant = true })

        projection.specification shouldBe VariantSpecification.Original
    }

    @Test
    fun `attributes decode independently of transformation and LQIP JSON`() {
        val projection =
            PostgresVariantDeliveryProjection(
                record().apply {
                    attributes =
                        JSONB.valueOf(
                            VariantParameterGenerator.generateImageVariantAttributes(expectedAttributes),
                        )
                },
            )

        projection.attributes shouldBe expectedAttributes
        shouldThrow<SerializationException> { projection.specification }
        shouldThrow<SerializationException> { projection.lqips }
    }

    @Test
    fun `transformation decodes independently of attributes and LQIP JSON`() {
        val projection =
            PostgresVariantDeliveryProjection(
                record().apply {
                    transformation =
                        JSONB.valueOf(
                            VariantParameterGenerator.generateImageVariantTransformations(
                                expectedTransformation,
                            ),
                        )
                },
            )

        projection.specification shouldBe VariantSpecification.Transformed(expectedTransformation)
        shouldThrow<SerializationException> { projection.attributes }
        shouldThrow<SerializationException> { projection.lqips }
    }

    @Test
    fun `LQIPs decode independently of attributes and transformation JSON`() {
        val projection =
            PostgresVariantDeliveryProjection(
                record().apply {
                    lqip = JSONB.valueOf(postgresJson.encodeToString(lqips))
                },
            )

        projection.lqips shouldBe lqips
        shouldThrow<SerializationException> { projection.attributes }
        shouldThrow<SerializationException> { projection.specification }
    }

    private fun record() =
        AssetVariantRecord(
            id = VariantId().value,
            objectStoreBucket = "bucket",
            objectStoreKey = "variant.png",
            originalVariant = false,
            attributes = invalidJson,
            transformation = invalidJson,
            lqip = invalidJson,
        )
}
