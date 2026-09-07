package io.konifer.infrastructure.objectstore.inmemory

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PresignedUrl
import io.konifer.infrastructure.objectstore.ObjectStoreTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class InMemoryObjectStoreTest : ObjectStoreTest() {
    override fun createObjectStore(): ObjectStore = InMemoryObjectStore()

    @Test
    fun `presigned urls are not supported`() =
        runTest {
            store.generatePresignedUrl(
                bucket = BUCKET_1,
                key = UuidCreator.getRandomBasedFast().toString(),
                ttl = 30.minutes,
            ) shouldBe PresignedUrl.NotSupported
        }
}
