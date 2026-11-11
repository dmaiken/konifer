package io.konifer.asset.fetch

import io.konifer.BasePostgresTestContainersTest
import io.konifer.ImageFactory.testImage
import io.konifer.client.assets.fetch.requestedTransformation
import io.konifer.common.http.MetadataResponse
import io.konifer.common.http.PaddingResponse
import io.konifer.common.http.TransformationResponse
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.Rotate
import io.konifer.infrastructure.datastore.postgres.createR2dbcDslContext
import io.konifer.infrastructure.datastore.postgres.postgresJson
import io.konifer.infrastructure.http.APP_CACHE_STATUS
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.testPostgres
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import konifer.jooq.tables.references.ASSET_VARIANT
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jooq.JSONB
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class PostgresTransformationCompatibilityTest : BasePostgresTestContainersTest() {
    @Test
    fun `future attributes and LQIPs preserve original processing and derived delivery`() =
        testPostgres(postgres) {
            val dsl = createR2dbcDslContext(postgres)
            val assets = konifer().assets("future-metadata")
            val image = testImage()
            val stored =
                assets
                    .newAsset()
                    .fromBytes(image.bytes, image.attributes.format)
                    .store()
                    .shouldBeSuccessful()
                    .body
            val futureMetadata = mutableMapOf<String, Pair<JSONB, JSONB>>()

            suspend fun addFutureMetadata(storeKey: String) {
                val row = dsl.selectFrom(ASSET_VARIANT).where(ASSET_VARIANT.OBJECT_STORE_KEY.eq(storeKey)).awaitSingle()
                val attributes =
                    JSONB.valueOf(
                        JsonObject(
                            postgresJson.parseToJsonElement(checkNotNull(row.attributes).data()).jsonObject +
                                ("futureAttribute" to JsonPrimitive(true)),
                        ).toString(),
                    )
                val lqips =
                    JSONB.valueOf(
                        JsonObject(
                            postgresJson.parseToJsonElement(checkNotNull(row.lqip).data()).jsonObject +
                                ("futurePreview" to JsonPrimitive("preview")),
                        ).toString(),
                    )
                dsl
                    .update(ASSET_VARIANT)
                    .set(ASSET_VARIANT.ATTRIBUTES, attributes)
                    .set(ASSET_VARIANT.LQIP, lqips)
                    .where(ASSET_VARIANT.ID.eq(row.id))
                    .awaitSingle()
                futureMetadata[storeKey] = attributes to lqips
            }

            val original = stored.variants.single()
            addFutureMetadata(original.storeKey)
            val originalContent =
                assets
                    .originalVariant()
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body
            originalContent.attributes shouldBe original.attributes
            originalContent.bytes.contentEquals(image.bytes) shouldBe true
            val variant = assets.variant(requestedTransformation { width = 100 })
            val generated = variant.fetchContent().shouldBeSuccessful().body
            val expected = assets.fetchInfo().shouldBeSuccessful().body
            val derived = expected.variants.single { !it.isOriginalVariant }
            addFutureMetadata(derived.storeKey)

            assets.fetchInfo().shouldBeSuccessful().body shouldBe expected
            val entry =
                assets
                    .fetchEntries(limit = 1)
                    .shouldBeSuccessful()
                    .body.entries
                    .single()
            entry.entryId shouldBe expected.entryId
            entry.variants shouldBe expected.variants
            variant.fetchContent().shouldBeSuccessful().body shouldBe generated
            assets
                .originalVariant()
                .fetchContent()
                .shouldBeSuccessful()
                .body shouldBe originalContent
            val cached = client.get("/assets/future-metadata/-/content?width=100")
            cached.status shouldBe HttpStatusCode.OK
            cached.headers[APP_CACHE_STATUS] shouldBe "hit"
            cached.bodyAsBytes().contentEquals(generated.bytes) shouldBe true
            futureMetadata.forEach { (key, metadata) ->
                val row = dsl.selectFrom(ASSET_VARIANT).where(ASSET_VARIANT.OBJECT_STORE_KEY.eq(key)).awaitSingle()
                (row.attributes to row.lqip) shouldBe metadata
            }
        }

    @Test
    fun `historical transformations with omitted fields remain visible and produce cache hits`() =
        testPostgres(postgres) {
            val dsl = createR2dbcDslContext(postgres)
            val assets = konifer().assets("legacy-transformations")
            val image = testImage()
            assets
                .newAsset()
                .fromBytes(image.bytes, image.attributes.format)
                .store()
                .shouldBeSuccessful()
            // Explicit request values keep this independent of future API default changes.
            val contentUrl =
                "/assets/legacy-transformations/-/content" +
                    "?width=100&height=75&format=jpg&fit=fit&gravity=center&rotate=0" +
                    "&flip=none&filter=none&blur=0&quality=80&pad=0&strip=&color-space=srgb"
            val generated = client.get(contentUrl)
            generated.status shouldBe HttpStatusCode.OK
            generated.headers[APP_CACHE_STATUS] shouldBe "miss"
            val expectedContent = generated.bodyAsBytes()
            val derived =
                assets
                    .fetchInfo()
                    .shouldBeSuccessful()
                    .body.variants
                    .single { !it.isOriginalVariant }
            val row = dsl.selectFrom(ASSET_VARIANT).where(ASSET_VARIANT.OBJECT_STORE_KEY.eq(derived.storeKey)).awaitSingle()
            val legacy = JSONB.valueOf("""{"width":100,"height":75,"format":"jpg"}""")
            dsl
                .update(ASSET_VARIANT)
                .set(ASSET_VARIANT.TRANSFORMATION, legacy)
                .where(ASSET_VARIANT.ID.eq(row.id))
                .awaitSingle()
            val expected =
                TransformationResponse(
                    width = 100,
                    height = 75,
                    format = "jpg",
                    fit = Fit.FIT,
                    gravity = Gravity.CENTER,
                    rotate = Rotate.ZERO,
                    flip = Flip.NONE,
                    filter = Filter.NONE,
                    blur = 0,
                    quality = 80,
                    padding = PaddingResponse(amount = 0, color = emptyList()),
                    metadata = MetadataResponse(strip = emptyList()),
                    colorSpace = "srgb",
                )

            val info = assets.fetchInfo().shouldBeSuccessful().body
            info.variants shouldHaveSize 2
            info.variants.single { !it.isOriginalVariant }.let {
                it.storeKey shouldBe derived.storeKey
                it.transformation shouldBe expected
            }
            val entry =
                assets
                    .fetchEntries(limit = 1)
                    .shouldBeSuccessful()
                    .body.entries
                    .single()
            entry.entryId shouldBe info.entryId
            entry.variants.single { !it.isOriginalVariant }.let {
                it.storeKey shouldBe derived.storeKey
                it.transformation shouldBe expected
            }

            val cached = client.get(contentUrl)
            cached.status shouldBe HttpStatusCode.OK
            cached.headers[APP_CACHE_STATUS] shouldBe "hit"
            cached.bodyAsBytes().contentEquals(expectedContent) shouldBe true
            assets
                .fetchInfo()
                .shouldBeSuccessful()
                .body.variants
                .map { it.storeKey } shouldBe info.variants.map { it.storeKey }
            dsl
                .selectCount()
                .from(ASSET_VARIANT)
                .where(ASSET_VARIANT.ASSET_ID.eq(row.assetId))
                .awaitSingle()
                .value1() shouldBe 2
            dsl
                .selectFrom(ASSET_VARIANT)
                .where(ASSET_VARIANT.ID.eq(row.id))
                .awaitSingle()
                .transformation shouldBe legacy
        }

    @Test
    fun `unsupported transformations are hidden and regenerated without changing the original or future variant`() =
        testPostgres(postgres) {
            val dsl = createR2dbcDslContext(postgres)
            val koniferClient = konifer()
            val assets = koniferClient.assets("compatibility")
            val image = testImage()
            val stored =
                assets
                    .newAsset()
                    .fromBytes(image.bytes, image.attributes.format)
                    .store()
                    .shouldBeSuccessful()
                    .body
            val original = stored.variants.single()
            // Content requests use HTTP directly to assert cache headers unavailable in the SDK result.
            val contentUrl = "/assets/compatibility/-/content?width=100"
            val generated = client.get(contentUrl)
            generated.status shouldBe HttpStatusCode.OK
            generated.headers[APP_CACHE_STATUS] shouldBe "miss"
            val expectedContent = generated.bodyAsBytes()
            val derived =
                assets
                    .fetchInfo()
                    .shouldBeSuccessful()
                    .body
                    .variants
                    .single { !it.isOriginalVariant }
            val row = dsl.selectFrom(ASSET_VARIANT).where(ASSET_VARIANT.OBJECT_STORE_KEY.eq(derived.storeKey)).awaitSingle()
            val futureJson =
                JSONB.valueOf(
                    JsonObject(
                        postgresJson.parseToJsonElement(checkNotNull(row.transformation).data()).jsonObject +
                            ("futureTransformation" to JsonPrimitive(true)),
                    ).toString(),
                )
            // Put the unsupported candidate first, including after regeneration.
            dsl
                .update(ASSET_VARIANT)
                .set(ASSET_VARIANT.TRANSFORMATION, futureJson)
                .set(ASSET_VARIANT.CREATED_AT, OffsetDateTime.now().plusMinutes(1))
                .where(ASSET_VARIANT.ID.eq(row.id))
                .awaitSingle()

            assets
                .fetchInfo()
                .shouldBeSuccessful()
                .body
                .variants
                .map { it.storeKey } shouldBe listOf(original.storeKey)
            val entries =
                assets
                    .fetchEntries(limit = 1)
                    .shouldBeSuccessful()
                    .body.entries
            entries.single().entryId shouldBe stored.entryId
            entries.single().variants.map { it.storeKey } shouldBe listOf(original.storeKey)
            val updated =
                assets
                    .updateAsset(stored)
                    .withAlt("updated")
                    .update()
                    .shouldBeSuccessful()
                    .body
            updated.alt shouldBe "updated"
            updated.variants.map { it.storeKey } shouldBe listOf(original.storeKey)
            val originalContent = client.get("/assets/compatibility/-/content")
            originalContent.status shouldBe HttpStatusCode.OK
            originalContent.headers[APP_CACHE_STATUS] shouldBe "hit"
            originalContent.bodyAsBytes()

            val regenerated = client.get(contentUrl)
            regenerated.status shouldBe HttpStatusCode.OK
            regenerated.headers[APP_CACHE_STATUS] shouldBe "miss"
            regenerated.bodyAsBytes().contentEquals(expectedContent) shouldBe true
            val cached = client.get(contentUrl)
            cached.status shouldBe HttpStatusCode.OK
            cached.headers[APP_CACHE_STATUS] shouldBe "hit"
            cached.bodyAsBytes().contentEquals(expectedContent) shouldBe true

            assets
                .fetchInfo()
                .shouldBeSuccessful()
                .body
                .variants shouldHaveSize 2
            dsl
                .selectFrom(ASSET_VARIANT)
                .where(ASSET_VARIANT.ID.eq(row.id))
                .awaitSingle()
                .transformation shouldBe futureJson
        }
}
