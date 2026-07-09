package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.ports.AssetRepository
import io.mockk.spyk
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
abstract class PostgresContainerizedTest {
    companion object {
        @JvmStatic
        @Container
        protected val postgres = postgresContainer()
    }

    protected val dslContext: DSLContext by lazy { spyk(createR2dbcDslContext(postgres)) }

    protected val assetRepository: AssetRepository by lazy {
        PostgresAssetRepository(
            dslContext = dslContext,
        )
    }

    @BeforeEach
    fun clearTables() {
        truncateTables(postgres)
    }
}
