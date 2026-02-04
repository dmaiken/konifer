package io.konifer.infrastructure.objectstore.inmemory

import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.objectstore.ObjectStoreTest
import io.konifer.infrastructure.objectstore.property.ObjectStoreProperties
import io.konifer.infrastructure.objectstore.property.RedirectProperties
import io.konifer.infrastructure.objectstore.property.RedirectStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID

class InMemoryObjectStoreTest : ObjectStoreTest() {
    override fun createObjectStore(): ObjectStore = InMemoryObjectStore()

    @Test
    fun `no url is returned for presigned redirect strategy`() =
        runTest {
            store.generateObjectUrl(
                bucket = BUCKET_1,
                key = UUID.randomUUID().toString(),
                properties =
                    ObjectStoreProperties(
                        redirect =
                            RedirectProperties(
                                strategy = RedirectStrategy.PRESIGNED,
                            ),
                    ),
            ) shouldBe null
        }
}
