package io.direkt.infrastructure.datastore.postgres.scheduling

import direkt.jooq.tables.references.OUTBOX
import io.direkt.domain.ports.ObjectRepository
import io.direkt.infrastructure.datastore.postgres.createR2dbcDslContext
import io.direkt.infrastructure.datastore.postgres.postgresContainer
import io.direkt.infrastructure.datastore.postgres.truncateTables
import io.direkt.infrastructure.objectstore.inmemory.InMemoryObjectRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.JSONB
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.UUID

@Testcontainers
class VariantReaperTest {
    companion object {
        @JvmStatic
        @Container
        private val postgres = postgresContainer()
    }

    val dslContext: DSLContext by lazy { createR2dbcDslContext(postgres) }
    val objectRepository: ObjectRepository = spyk(InMemoryObjectRepository())

    val file: File =
        Files.createTempFile("test", ".txt").toFile().apply {
            deleteOnExit()
        }

    @BeforeEach
    fun clearTables() {
        truncateTables(postgres)
    }

    @Test
    fun `can reap variants`() =
        runTest {
            objectRepository.persist(
                bucket = "bucket",
                key = "key",
                file = file,
            )

            val eventId = UUID.randomUUID()
            dslContext
                .insertInto(OUTBOX)
                .set(OUTBOX.ID, eventId)
                .set(OUTBOX.EVENT_TYPE, ReapVariantEvent.TYPE)
                .set(
                    OUTBOX.PAYLOAD,
                    JSONB.valueOf(
                        Json.encodeToString(
                            ReapVariantEvent(
                                objectStoreBucket = "bucket",
                                objectStoreKey = "key",
                            ),
                        ),
                    ),
                ).set(OUTBOX.CREATED_AT, LocalDateTime.now())
                .awaitFirstOrNull()

            VariantReaper.invoke(
                dslContext = dslContext,
                objectRepository = objectRepository,
            )

            objectRepository.exists(
                bucket = "bucket",
                key = "key",
            ) shouldBe false
            dslContext
                .select()
                .from(OUTBOX)
                .where(OUTBOX.ID.eq(eventId))
                .awaitFirstOrNull() shouldBe null
        }

    @Test
    fun `if variant fails to be reaped then outbox event is not deleted`() =
        runTest {
            objectRepository.persist(
                bucket = "bucket",
                key = "key",
                file = file,
            )

            val eventId = UUID.randomUUID()
            dslContext
                .insertInto(OUTBOX)
                .set(OUTBOX.ID, eventId)
                .set(OUTBOX.EVENT_TYPE, ReapVariantEvent.TYPE)
                .set(
                    OUTBOX.PAYLOAD,
                    JSONB.valueOf(
                        Json.encodeToString(
                            ReapVariantEvent(
                                objectStoreBucket = "bucket",
                                objectStoreKey = "key",
                            ),
                        ),
                    ),
                ).set(OUTBOX.CREATED_AT, LocalDateTime.now())
                .awaitFirstOrNull()
            coEvery {
                objectRepository.delete(any(), any())
            } throws RuntimeException()

            shouldNotThrowAny {
                VariantReaper.invoke(
                    dslContext = dslContext,
                    objectRepository = objectRepository,
                )
            }

            objectRepository.exists(
                bucket = "bucket",
                key = "key",
            ) shouldBe true
            dslContext
                .select()
                .from(OUTBOX)
                .where(OUTBOX.ID.eq(eventId))
                .awaitFirstOrNull() shouldNotBe null
        }

    @Test
    fun `if variant fails to be reaped then others are attempted`() =
        runTest {
            objectRepository.persist(
                bucket = "bucket",
                key = "key1",
                file = file,
            )
            objectRepository.persist(
                bucket = "bucket",
                key = "key2",
                file = file,
            )

            val eventId1 = UUID.randomUUID()
            dslContext
                .insertInto(OUTBOX)
                .set(OUTBOX.ID, eventId1)
                .set(OUTBOX.EVENT_TYPE, ReapVariantEvent.TYPE)
                .set(
                    OUTBOX.PAYLOAD,
                    JSONB.valueOf(
                        Json.encodeToString(
                            ReapVariantEvent(
                                objectStoreBucket = "bucket",
                                objectStoreKey = "key1",
                            ),
                        ),
                    ),
                ).set(OUTBOX.CREATED_AT, LocalDateTime.now())
                .awaitFirstOrNull()
            val eventId2 = UUID.randomUUID()
            dslContext
                .insertInto(OUTBOX)
                .set(OUTBOX.ID, eventId2)
                .set(OUTBOX.EVENT_TYPE, ReapVariantEvent.TYPE)
                .set(
                    OUTBOX.PAYLOAD,
                    JSONB.valueOf(
                        Json.encodeToString(
                            ReapVariantEvent(
                                objectStoreBucket = "bucket",
                                objectStoreKey = "key2",
                            ),
                        ),
                    ),
                ).set(OUTBOX.CREATED_AT, LocalDateTime.now())
                .awaitFirstOrNull()
            coEvery {
                objectRepository.delete(any(), key = "key1")
            } throws RuntimeException()

            shouldNotThrowAny {
                VariantReaper.invoke(
                    dslContext = dslContext,
                    objectRepository = objectRepository,
                )
            }

            objectRepository.exists(
                bucket = "bucket",
                key = "key1",
            ) shouldBe true
            objectRepository.exists(
                bucket = "bucket",
                key = "key2",
            ) shouldBe false
            dslContext
                .select()
                .from(OUTBOX)
                .where(OUTBOX.ID.eq(eventId1))
                .awaitFirstOrNull() shouldNotBe null
            dslContext
                .select()
                .from(OUTBOX)
                .where(OUTBOX.ID.eq(eventId2))
                .awaitFirstOrNull() shouldBe null
        }

    @Test
    fun `does not fail if nothing needs to be reaped`() =
        runTest {
            shouldNotThrowAny {
                VariantReaper.invoke(
                    dslContext = dslContext,
                    objectRepository = objectRepository,
                )
            }
        }
}
