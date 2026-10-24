package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.retention.CacheProperties
import io.konifer.infrastructure.datastore.createPendingAsset
import io.konifer.infrastructure.datastore.createPendingVariant
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Testcontainers
class TimestampMigrationTest {
    companion object {
        @JvmStatic
        @Container
        private val postgres =
            PostgreSQLContainer("postgres:17-alpine")
                .withCommand("postgres", "-c", "timezone=America/Denver")

        private val migrations =
            listOf(
                "V1__create_asset_tree_table.sql",
                "V2__create_asset_label_table.sql",
                "V3__create_asset_tag_table.sql",
                "V4__create_db_scheduler_tables.sql",
                "V5__create_path_entry_counter_table.sql",
                "V6__add_expires_at_variant_table.sql",
                "V7__add_last_accessed_at_variant_table.sql",
                "V8__add_prompt_embedding_table.sql",
                "V9__add_access_score_asset_variant_table.sql",
                "V10__use_timezone_aware_timestamps.sql",
            )
        private val timestampColumns =
            mapOf(
                "asset_tree" to listOf("created_at", "modified_at"),
                "asset_variant" to listOf("created_at", "uploaded_at", "expires_at", "last_accessed_at", "access_score_as_of"),
                "asset_label" to listOf("created_at"),
                "asset_tag" to listOf("created_at"),
                "outbox" to listOf("created_at"),
                "prompt_embedding" to listOf("created_at"),
            )
    }

    @Test
    fun `upgrading UTC local timestamps preserves instants precision nulls indexes and defaults in a non UTC session`() {
        connection().use { connection ->
            connection.createIsolatedSchema()
            connection.migrate(migrations.dropLast(1))
            val assetId = UUID.randomUUID()
            val variantId = UUID.randomUUID()
            val originalId = UUID.randomUUID()
            val timestamp = "TIMESTAMP '2026-01-02 03:04:05.123456'"
            connection.executeSql(
                """
                INSERT INTO asset_tree (id, path, source, created_at, modified_at)
                VALUES ('$assetId', 'example', 'UPLOAD', $timestamp, $timestamp);
                INSERT INTO asset_variant (
                    id, asset_id, object_store_bucket, object_store_key, transformation, attributes, lqip,
                    original_variant, created_at, uploaded_at, expires_at, last_accessed_at, access_score_as_of
                ) VALUES (
                    '$variantId', '$assetId', 'assets', 'variant', '{"width":10}', '{}', '{}',
                    false, $timestamp, $timestamp, $timestamp, $timestamp, $timestamp
                );
                INSERT INTO asset_variant (
                    id, asset_id, object_store_bucket, object_store_key, transformation, attributes, lqip,
                    original_variant, created_at, access_score_as_of
                ) VALUES ('$originalId', '$assetId', 'assets', 'original', '{}', '{}', '{}', true, $timestamp, $timestamp);
                INSERT INTO asset_label VALUES ('${UUID.randomUUID()}', '$assetId', 'kind', 'example', $timestamp);
                INSERT INTO asset_tag VALUES ('${UUID.randomUUID()}', '$assetId', 'example', $timestamp);
                INSERT INTO outbox VALUES ('${UUID.randomUUID()}', 'example', '{}', $timestamp);
                INSERT INTO prompt_embedding VALUES ('${UUID.randomUUID()}', 'model', 'prompt', ARRAY[1.0]::real[], $timestamp);
                """.trimIndent(),
            )
            val indexesBefore = connection.indexes()
            connection.migrate(migrations.takeLast(1))
            connection.assertTimestampColumnTypes()
            connection.indexes() shouldBe indexesBefore
            val expected = Instant.parse("2026-01-02T03:04:05.123456Z")
            timestampColumns.forEach { (table, columns) ->
                connection.createStatement().use { statement ->
                    val filter = if (table == "asset_variant") " WHERE id = '$variantId'" else ""
                    statement.executeQuery("SELECT * FROM $table$filter").use { result ->
                        result.next() shouldBe true
                        columns.forEach { column ->
                            result.getObject(column, OffsetDateTime::class.java).toInstant() shouldBe expected
                        }
                    }
                }
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM asset_variant WHERE id = '$originalId'").use { result ->
                    result.next() shouldBe true
                    for (column in listOf("uploaded_at", "expires_at", "last_accessed_at")) {
                        result.getObject(column) shouldBe null
                    }
                }
            }
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        """
                        INSERT INTO asset_variant (
                            id, asset_id, object_store_bucket, object_store_key, transformation, attributes, lqip, original_variant, created_at
                        ) VALUES ('${UUID.randomUUID()}', '$assetId', 'assets', 'defaults', '{"width":20}', '{}', '{}', false, CURRENT_TIMESTAMP)
                        RETURNING access_score_as_of, CURRENT_TIMESTAMP AS expected
                        """.trimIndent(),
                    ).use { result ->
                        result.next() shouldBe true
                        result.getObject("access_score_as_of", OffsetDateTime::class.java).toInstant() shouldBe
                            result.getObject("expected", OffsetDateTime::class.java).toInstant()
                    }
            }
        }
    }

    @Test
    fun `fresh migrations use timezone aware timestamps and leave scheduler columns unchanged`() {
        connection().use { connection ->
            connection.createIsolatedSchema()
            connection.migrate(migrations)
            connection.assertTimestampColumnTypes()
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        """
                        SELECT data_type FROM information_schema.columns
                        WHERE table_schema = current_schema() AND table_name = 'scheduled_tasks' AND column_name = 'execution_time'
                        """.trimIndent(),
                    ).use { result ->
                        result.next() shouldBe true
                        result.getString(1) shouldBe "timestamp with time zone"
                    }
            }
        }
    }

    @Test
    fun `repositories round trip instants through a database with a non UTC default timezone`() =
        runTest {
            val dsl = createR2dbcDslContext(postgres)
            dsl.resultQuery("SHOW TIME ZONE").awaitSingle().get(0, String::class.java) shouldBe "America/Denver"
            val repository = PostgresAssetRepository(dsl)
            val asset = repository.storeNew(createPendingAsset(path = "/timestamps"))
            repository.markReady(asset.markReady(Instant.now()))
            val expiry = Instant.parse("2030-01-02T03:04:05.123456Z")
            val pending =
                repository.storeNewVariant(
                    createPendingVariant(
                        assetId = asset.id,
                        transformation =
                            Transformation(
                                width = 20.toDimension(),
                                height = 10.toDimension(),
                                format = ImageFormat.PNG,
                                colorSpace = ColorSpace.SRGB,
                            ),
                        expiresAt = expiry,
                    ),
                )
            repository.markUploaded(pending.markReady(Instant.now()), CacheProperties())
            val fetched = repository.fetchByPath(asset.path, asset.entryId)
            fetched shouldNotBe null
            fetched!!.createdAt.truncatedTo(ChronoUnit.MILLIS) shouldBe asset.createdAt.truncatedTo(ChronoUnit.MILLIS)
            fetched.variants.single { !it.isOriginalVariant }.expiresAt shouldBe expiry
            fetched.variants.single { it.isOriginalVariant }.expiresAt shouldBe null
            repository.fetchForDelivery(asset.path, asset.entryId)!!.modifiedAt.truncatedTo(ChronoUnit.MILLIS) shouldBe
                fetched.modifiedAt.truncatedTo(ChronoUnit.MILLIS)
        }

    private fun connection(): Connection = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun Connection.createIsolatedSchema() {
        executeSql("CREATE EXTENSION IF NOT EXISTS ltree")
        val schema = "timestamps_${UUID.randomUUID().toString().replace("-", "")}"
        executeSql("CREATE SCHEMA $schema; SET search_path TO $schema, public; SET TIME ZONE 'America/Denver'")
    }

    private fun Connection.executeSql(sql: String) {
        createStatement().use { it.execute(sql) }
    }

    private fun Connection.migrate(names: List<String>) {
        names.forEach { name ->
            val sql =
                checkNotNull(
                    TimestampMigrationTest::class.java.getResourceAsStream("/db/migration/$name"),
                ).bufferedReader().use { it.readText() }
            executeSql(sql)
        }
    }

    private fun Connection.indexes(): Set<String> =
        createStatement().use { statement ->
            statement.executeQuery("SELECT indexname FROM pg_indexes WHERE schemaname = current_schema()").use { result ->
                buildSet { while (result.next()) add(result.getString(1)) }
            }
        }

    private fun Connection.assertTimestampColumnTypes() {
        timestampColumns.forEach { (table, columns) ->
            columns.forEach { column ->
                createStatement().use { statement ->
                    statement
                        .executeQuery(
                            """
                            SELECT data_type FROM information_schema.columns
                            WHERE table_schema = current_schema() AND table_name = '$table' AND column_name = '$column'
                            """.trimIndent(),
                        ).use { result ->
                            result.next() shouldBe true
                            result.getString(1) shouldBe "timestamp with time zone"
                        }
                }
            }
        }
    }
}
