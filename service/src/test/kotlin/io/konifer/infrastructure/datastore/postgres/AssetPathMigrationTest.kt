package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.asset.AssetPath
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import name.nkonev.r2dbc.migrate.core.R2dbcMigrate
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties
import name.nkonev.r2dbc.migrate.reader.MigrateResourceReader
import name.nkonev.r2dbc.migrate.reader.ReflectionsClasspathResourceReader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

@Testcontainers
class AssetPathMigrationTest {
    companion object {
        @JvmStatic
        @Container
        private val postgres = postgresContainer().withReuse(false)
    }

    @BeforeEach
    fun initializeLegacySchema() {
        connection().use { connection ->
            connection.createStatement().use {
                it.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public; CREATE EXTENSION ltree;")
            }
        }
        migrate(includeEscaping = false)
    }

    @Test
    fun `migration preserves assets and counters without underscore collisions`() {
        val paths = listOf("/", "/a_b", "/a__b", "/a_2eb", "/A_1/child")
        val ids = paths.associateWith { UUID.randomUUID() }
        connection().use { connection ->
            ids.forEach { (path, id) -> connection.insertAsset(legacyPath(path), id) shouldBe 0L }
            connection.createStatement().use {
                it.executeUpdate("UPDATE path_entry_counter SET last_entry_id = 9 WHERE path = 'root.a_b'")
                it.executeUpdate("INSERT INTO path_entry_counter VALUES ('root.gone_name', 41)")
            }
        }

        migrate(includeEscaping = true)
        // Re-running startup migrations must not escape the paths again.
        migrate(includeEscaping = true)

        connection().use { connection ->
            ids.forEach { (path, id) ->
                connection
                    .prepareStatement(
                        "SELECT path::text, entry_id, created_at, modified_at FROM asset_tree WHERE id = ?",
                    ).use { statement ->
                        statement.setObject(1, id)
                        statement.executeQuery().use { result ->
                            result.next() shouldBe true
                            result.getString("path") shouldBe AssetPath(path).toLtree().data()
                            result.getLong("entry_id") shouldBe 0L
                            result.getTimestamp("created_at").toInstant().toString() shouldBe "2020-01-01T00:00:00Z"
                            result.getTimestamp("modified_at").toInstant().toString() shouldBe "2020-01-01T00:00:00Z"
                        }
                    }
            }
            connection.insertAsset(AssetPath("/a_b").toLtree().data()) shouldBe 10L
            connection.insertAsset(AssetPath("/gone_name").toLtree().data()) shouldBe 42L
            shouldThrow<SQLException> { connection.insertAsset("root.legacy") }.sqlState shouldBe "23514"
            shouldThrow<SQLException> {
                connection.createStatement().use { it.executeUpdate("INSERT INTO path_entry_counter VALUES ('root.legacy', 0)") }
            }.sqlState shouldBe "23514"
        }
    }

    @Test
    fun `oversized counter label rolls back both tables and permits retry after repair`() {
        val oversized = "_".repeat(501)
        connection().use { connection ->
            connection.insertAsset("root.ok_name")
            connection.prepareStatement("INSERT INTO path_entry_counter VALUES (?::ltree, 7)").use {
                it.setString(1, "root.$oversized")
                it.executeUpdate()
            }
        }

        shouldThrow<Exception> { migrate(includeEscaping = true) }

        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT path::text FROM asset_tree").use {
                    it.next() shouldBe true
                    it.getString(1) shouldBe "root.ok_name"
                }
                statement.executeQuery("SELECT last_entry_id FROM path_entry_counter WHERE path = 'root.$oversized'").use {
                    it.next() shouldBe true
                    it.getLong(1) shouldBe 7L
                }
                statement.executeUpdate(
                    "UPDATE path_entry_counter SET path = 'root.repaired' WHERE path = 'root.$oversized'",
                )
            }
        }
        migrate(includeEscaping = true)
        connection().use {
            it.insertAsset(AssetPath("/ok_name").toLtree().data()) shouldBe 1L
            it.insertAsset(AssetPath("/repaired").toLtree().data()) shouldBe 8L
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun migrate(includeEscaping: Boolean) {
        val factory =
            connectToPostgres(
                PostgresProperties(
                    database = postgres.databaseName,
                    user = postgres.username,
                    host = postgres.host,
                    port = postgres.getMappedPort(5432),
                    password = postgres.password,
                    sslMode = "disable",
                ),
            )
        val reader = ReflectionsClasspathResourceReader()
        val filteredReader =
            MigrateResourceReader { path ->
                reader.getResources(path).filter { includeEscaping || !it.filename.startsWith("V11__") }
            }
        val properties = R2dbcMigrateProperties().apply { setResourcesPath("db/migration") }
        R2dbcMigrate.migrate(factory, properties, filteredReader, null, null).block()
    }

    private fun Connection.insertAsset(
        path: String,
        id: UUID = UUID.randomUUID(),
    ): Long =
        prepareStatement(
            """
            INSERT INTO asset_tree (id, path, entry_id, source, is_ready, created_at, modified_at)
            VALUES (?, ?::ltree, 0, 'UPLOAD', false, '2020-01-01T00:00:00Z', '2020-01-01T00:00:00Z')
            RETURNING entry_id
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, path)
            statement.executeQuery().use {
                check(it.next())
                it.getLong(1)
            }
        }

    private fun legacyPath(path: String): String = if (path == "/") "root" else "root" + path.replace('/', '.')
}
