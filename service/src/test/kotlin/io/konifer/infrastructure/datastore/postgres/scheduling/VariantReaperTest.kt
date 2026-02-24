package io.konifer.infrastructure.datastore.postgres.scheduling

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.datastore.postgres.createR2dbcDslContext
import io.konifer.infrastructure.datastore.postgres.postgresContainer
import io.konifer.infrastructure.datastore.postgres.truncateTables
import io.konifer.infrastructure.objectstore.inmemory.InMemoryObjectStore
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeByte
import io.mockk.coEvery
import io.mockk.spyk
import konifer.jooq.tables.references.OUTBOX
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.JSONB
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

@Testcontainers
class VariantReaperTest {
    companion object {
        @JvmStatic
        @Container
        private val postgres = postgresContainer()
    }

    val dslContext: DSLContext by lazy { createR2dbcDslContext(postgres) }
    val objectStore: ObjectStore = spyk(InMemoryObjectStore())

    val channel =
        runBlocking {
            ByteChannel().also {
                it.writeByte(1)
                it.close()
            }
        }

    @BeforeEach
    fun clearTables() {
        truncateTables(postgres)
    }

    @Test
    fun `can reap variants`() =
        runTest {
            objectStore.persist(
                bucket = "bucket",
                key = "key",
                channel = channel,
            )

            val eventId = UuidCreator.getRandomBasedFast()
            dslContext
                .insertInto(OUTBOX)
                .set(OUTBOX.ID, eventId)
                .set(OUTBOX.EVENT_TYPE, VariantDeletedEvent.TYPE)
                .set(
                    OUTBOX.PAYLOAD,
                    JSONB.valueOf(
                        Json.encodeToString(
                            VariantDeletedEvent(
                                objectStoreBucket = "bucket",
                                objectStoreKey = "key",
                            ),
                        ),
                    ),
                ).set(OUTBOX.CREATED_AT, LocalDateTime.now())
                .awaitFirstOrNull()

            VariantReaper.invoke(
                dslContext = dslContext,
                objectStore = objectStore,
            )

            objectStore.exists(
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
            objectStore.persist(
                bucket = "bucket",
                key = "key",
                channel = channel,
            )

            val eventId = UuidCreator.getRandomBasedFast()
            dslContext
                .insertInto(OUTBOX)
                .set(OUTBOX.ID, eventId)
                .set(OUTBOX.EVENT_TYPE, VariantDeletedEvent.TYPE)
                .set(
                    OUTBOX.PAYLOAD,
                    JSONB.valueOf(
                        Json.encodeToString(
                            VariantDeletedEvent(
                                objectStoreBucket = "bucket",
                                objectStoreKey = "key",
                            ),
                        ),
                    ),
                ).set(OUTBOX.CREATED_AT, LocalDateTime.now())
                .awaitFirstOrNull()
            coEvery {
                objectStore.delete(any(), any())
            } throws RuntimeException()

            shouldNotThrowAny {
                VariantReaper.invoke(
                    dslContext = dslContext,
                    objectStore = objectStore,
                )
            }

            objectStore.exists(
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
            objectStore.persist(
                bucket = "bucket",
                key = "key1",
                channel = channel,
            )
            objectStore.persist(
                bucket = "bucket",
                key = "key2",
                channel = channel,
            )

            val eventId1 = UuidCreator.getRandomBasedFast()
            dslContext
                .insertInto(OUTBOX)
                .set(OUTBOX.ID, eventId1)
                .set(OUTBOX.EVENT_TYPE, VariantDeletedEvent.TYPE)
                .set(
                    OUTBOX.PAYLOAD,
                    JSONB.valueOf(
                        Json.encodeToString(
                            VariantDeletedEvent(
                                objectStoreBucket = "bucket",
                                objectStoreKey = "key1",
                            ),
                        ),
                    ),
                ).set(OUTBOX.CREATED_AT, LocalDateTime.now())
                .awaitFirstOrNull()
            val eventId2 = UuidCreator.getRandomBasedFast()
            dslContext
                .insertInto(OUTBOX)
                .set(OUTBOX.ID, eventId2)
                .set(OUTBOX.EVENT_TYPE, VariantDeletedEvent.TYPE)
                .set(
                    OUTBOX.PAYLOAD,
                    JSONB.valueOf(
                        Json.encodeToString(
                            VariantDeletedEvent(
                                objectStoreBucket = "bucket",
                                objectStoreKey = "key2",
                            ),
                        ),
                    ),
                ).set(OUTBOX.CREATED_AT, LocalDateTime.now())
                .awaitFirstOrNull()
            coEvery {
                objectStore.delete(any(), key = "key1")
            } throws RuntimeException()

            shouldNotThrowAny {
                VariantReaper.invoke(
                    dslContext = dslContext,
                    objectStore = objectStore,
                )
            }

            objectStore.exists(
                bucket = "bucket",
                key = "key1",
            ) shouldBe true
            objectStore.exists(
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
    fun `limit is respected when reaping variants`() =
        runTest {
            objectStore.persist(
                bucket = "bucket",
                key = "key1",
                channel = channel,
            )
            objectStore.persist(
                bucket = "bucket",
                key = "key2",
                channel = channel,
            )

            val eventId1 = UuidCreator.getRandomBasedFast()
            dslContext
                .insertInto(OUTBOX)
                .set(OUTBOX.ID, eventId1)
                .set(OUTBOX.EVENT_TYPE, VariantDeletedEvent.TYPE)
                .set(
                    OUTBOX.PAYLOAD,
                    JSONB.valueOf(
                        Json.encodeToString(
                            VariantDeletedEvent(
                                objectStoreBucket = "bucket",
                                objectStoreKey = "key1",
                            ),
                        ),
                    ),
                ).set(OUTBOX.CREATED_AT, LocalDateTime.now())
                .awaitFirstOrNull()
            val eventId2 = UuidCreator.getRandomBasedFast()
            dslContext
                .insertInto(OUTBOX)
                .set(OUTBOX.ID, eventId2)
                .set(OUTBOX.EVENT_TYPE, VariantDeletedEvent.TYPE)
                .set(
                    OUTBOX.PAYLOAD,
                    JSONB.valueOf(
                        Json.encodeToString(
                            VariantDeletedEvent(
                                objectStoreBucket = "bucket",
                                objectStoreKey = "key2",
                            ),
                        ),
                    ),
                ).set(OUTBOX.CREATED_AT, LocalDateTime.now())
                .awaitFirstOrNull()

            VariantReaper.invoke(
                dslContext = dslContext,
                objectStore = objectStore,
                reapLimit = 1,
            )

            objectStore.exists(
                bucket = "bucket",
                key = "key1",
            ) shouldBe false
            objectStore.exists(
                bucket = "bucket",
                key = "key2",
            ) shouldBe true
            dslContext
                .select()
                .from(OUTBOX)
                .where(OUTBOX.ID.eq(eventId1))
                .awaitFirstOrNull() shouldBe null
            dslContext
                .select()
                .from(OUTBOX)
                .where(OUTBOX.ID.eq(eventId2))
                .awaitFirstOrNull() shouldNotBe null
        }

    @Test
    fun `does not fail if nothing needs to be reaped`() =
        runTest {
            shouldNotThrowAny {
                VariantReaper.invoke(
                    dslContext = dslContext,
                    objectStore = objectStore,
                )
            }
        }
}
