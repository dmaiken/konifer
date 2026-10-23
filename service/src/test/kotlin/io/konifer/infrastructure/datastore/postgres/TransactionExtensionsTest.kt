package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.asset.AssetId
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class TransactionExtensionsTest {
    companion object {
        @JvmStatic
        @Container
        private val postgres = postgresContainer()
    }

    private val dslContext by lazy { createR2dbcDslContext(postgres) }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `advisory lock protects the transaction and is released on completion`(rollback: Boolean) =
        runBlocking<Unit> {
            withTimeout(10.seconds) {
                val key = AdvisoryLockKeyProvider.assetLockKey(AssetId())
                val lockAcquired = CompletableDeferred<Unit>()
                val releaseTransaction = CompletableDeferred<Unit>()
                val rollbackFailure = IllegalStateException("Roll back the protected transaction")
                val transaction =
                    async {
                        runCatching {
                            dslContext.contextualizedTransactionCoroutine(advisoryLockKey = key) {
                                lockAcquired.complete(Unit)
                                releaseTransaction.await()
                                if (rollback) throw rollbackFailure
                            }
                        }
                    }

                try {
                    lockAcquired.await()
                    canAcquireLock(key) shouldBe false
                } finally {
                    releaseTransaction.complete(Unit)
                }

                val result = transaction.await()
                if (rollback) {
                    result.exceptionOrNull() shouldBe rollbackFailure
                } else {
                    result.getOrThrow()
                }
                canAcquireLock(key) shouldBe true
            }
        }

    private suspend fun canAcquireLock(key: Long): Boolean =
        dslContext.transactionCoroutine { transaction ->
            checkNotNull(
                transaction
                    .dsl()
                    .select(DSL.field("pg_try_advisory_xact_lock({0})", Boolean::class.java, DSL.value(key)))
                    .awaitSingle()
                    .value1(),
            )
        }
}
