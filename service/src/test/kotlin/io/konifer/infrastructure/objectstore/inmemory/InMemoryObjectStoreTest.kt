package io.konifer.infrastructure.objectstore.inmemory

import io.konifer.domain.path.RedirectProperties
import io.konifer.domain.path.RedirectStrategy
import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.objectstore.ObjectStoreTest
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
                    RedirectProperties(
                        strategy = RedirectStrategy.PRESIGNED,
                    ),
            ) shouldBe null
        }
}
