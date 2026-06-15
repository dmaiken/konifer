package io.konifer.infrastructure.datastore.postgres.scheduling

import io.konifer.domain.ports.AssetRepository
import io.konifer.infrastructure.datastore.postgres.PostgresAssetRepository
import io.konifer.infrastructure.datastore.postgres.createR2dbcDslContext
import io.konifer.infrastructure.datastore.postgres.postgresContainer
import io.konifer.infrastructure.datastore.postgres.truncateTables
import io.mockk.spyk
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
abstract class SchedulerTest {
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
