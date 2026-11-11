package io.konifer.infrastructure.datastore.postgres.projection

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.InvalidTransformationException
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.infrastructure.datastore.postgres.postgresJson
import io.konifer.infrastructure.datastore.postgres.schema.VariantParameterGenerator
import io.konifer.infrastructure.datastore.postgres.toVariantSpecification
import io.konifer.infrastructure.datastore.postgres.toVariantSpecificationOrNull
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import konifer.jooq.tables.records.AssetVariantRecord
import kotlinx.serialization.SerializationException
import org.jooq.JSONB
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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
    fun `identity and storage fields do not decode attributes or LQIPs`() {
        val record = record()
        val projection = checkNotNull(PostgresVariantDeliveryProjection.from(record))

        projection.id shouldBe VariantId(checkNotNull(record.id))
        projection.objectStoreBucket shouldBe "bucket"
        projection.objectStoreKey shouldBe "variant.png"
        projection.isOriginalVariant shouldBe false
    }

    @Test
    fun `original specification does not decode stored transformation JSON`() {
        val projection =
            checkNotNull(
                PostgresVariantDeliveryProjection.from(
                    record().apply {
                        originalVariant = true
                        transformation =
                            invalidJson
                    },
                ),
            )

        projection.specification shouldBe VariantSpecification.Original
    }

    @Test
    fun `attributes decode independently of LQIP JSON`() {
        val projection =
            checkNotNull(
                PostgresVariantDeliveryProjection.from(
                    record().apply {
                        attributes =
                            JSONB.valueOf(
                                VariantParameterGenerator.generateImageVariantAttributes(expectedAttributes),
                            )
                    },
                ),
            )

        projection.attributes shouldBe expectedAttributes
        projection.specification shouldBe VariantSpecification.Transformed(expectedTransformation)
        shouldThrow<SerializationException> { projection.lqips }
    }

    @Test
    fun `transformation decodes independently of attributes and LQIP JSON`() {
        val projection =
            checkNotNull(
                PostgresVariantDeliveryProjection.from(
                    record().apply {
                        transformation =
                            JSONB.valueOf(
                                VariantParameterGenerator.generateImageVariantTransformations(
                                    expectedTransformation,
                                ),
                            )
                    },
                ),
            )

        projection.specification shouldBe VariantSpecification.Transformed(expectedTransformation)
        shouldThrow<SerializationException> { projection.attributes }
        shouldThrow<SerializationException> { projection.lqips }
    }

    @Test
    fun `LQIPs decode independently of attributes JSON`() {
        val projection =
            checkNotNull(
                PostgresVariantDeliveryProjection.from(
                    record().apply {
                        lqip = JSONB.valueOf(postgresJson.encodeToString(lqips))
                    },
                ),
            )

        projection.lqips shouldBe lqips
        shouldThrow<SerializationException> { projection.attributes }
        projection.specification shouldBe VariantSpecification.Transformed(expectedTransformation)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """"futureTransformation":true""",
            """"padding":{"futurePadding":1}""",
            """"metadata":{"strip":["futureMetadata"]}""",
            """"fit":"futureFit"""",
            """"pixels":{"channels":"RGB","layout":"futureLayout"}""",
            """"blur":"invalid"""",
        ],
    )
    fun `unreadable transformations are excluded but strict conversion still fails`(field: String) {
        val record =
            record().apply {
                transformation = JSONB.valueOf("""{"width":50,"height":80,"format":"png",$field}""")
            }

        record.toVariantSpecificationOrNull() shouldBe null
        PostgresVariantDeliveryProjection.from(record) shouldBe null
        shouldThrow<SerializationException> { record.toVariantSpecification() }
    }

    @Test
    fun `historical minimal transformation uses persisted defaults`() {
        val record =
            record().apply {
                transformation = JSONB.valueOf("""{"width":50,"height":80,"format":"png"}""")
            }

        checkNotNull(PostgresVariantDeliveryProjection.from(record)).specification shouldBe
            VariantSpecification.Transformed(expectedTransformation)
    }

    @Test
    fun `accepted specification is resolved once`() {
        val record = record()
        val projection = checkNotNull(PostgresVariantDeliveryProjection.from(record))
        record.transformation = invalidJson

        repeat(2) { projection.specification shouldBe VariantSpecification.Transformed(expectedTransformation) }
    }

    @Test
    fun `malformed transformation is excluded`() {
        PostgresVariantDeliveryProjection.from(record().apply { transformation = invalidJson }) shouldBe null
    }

    @Test
    fun `domain validation and missing database fields still fail`() {
        val invalidQuality =
            record().apply {
                transformation = JSONB.valueOf("""{"width":50,"height":80,"format":"png","quality":0}""")
            }
        shouldThrow<InvalidTransformationException> { PostgresVariantDeliveryProjection.from(invalidQuality) }
        shouldThrow<IllegalStateException> { PostgresVariantDeliveryProjection.from(record().apply { transformation = null }) }
    }

    private fun record() =
        AssetVariantRecord(
            id = VariantId().value,
            objectStoreBucket = "bucket",
            objectStoreKey = "variant.png",
            originalVariant = false,
            attributes = invalidJson,
            transformation = JSONB.valueOf(VariantParameterGenerator.generateImageVariantTransformations(expectedTransformation)),
            lqip = invalidJson,
        )
}
