package io.konifer

import io.konifer.infrastructure.datastore.postgres.postgresContainer
import io.konifer.infrastructure.datastore.postgres.truncateTables
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
open class BaseTestContainersTest : BaseFunctionalTest() {
    companion object {
        @JvmStatic
        @Container
        protected val postgres = postgresContainer()
    }

    @BeforeEach
    fun clearTables() {
        truncateTables(postgres)
    }
}
