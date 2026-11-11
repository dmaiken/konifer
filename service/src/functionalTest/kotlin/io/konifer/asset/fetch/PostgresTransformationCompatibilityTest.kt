package io.konifer.asset.fetch

import io.konifer.BasePostgresTestContainersTest
import io.konifer.ImageFactory.testImage
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
