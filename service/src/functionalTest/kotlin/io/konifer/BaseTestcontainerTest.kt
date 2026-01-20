package io.konifer

import io.konifer.config.LocalstackContainerManager
import io.konifer.config.PostgresTestContainerManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

open class BaseTestcontainerTest {
    companion object {
        lateinit var postgres: PostgresTestContainerManager
        lateinit var localstack: LocalstackContainerManager

        @BeforeAll
        @JvmStatic
        fun setup() {
            postgres = PostgresTestContainerManager()
            localstack = LocalstackContainerManager()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            postgres.stop()
            localstack.stop()
        }

        const val BOUNDARY = "boundary"
    }

    @BeforeEach
    fun beforeEach() {
        postgres.clearTables()
    }
}
