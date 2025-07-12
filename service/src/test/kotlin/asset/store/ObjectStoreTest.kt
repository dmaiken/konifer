package asset.store

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID

abstract class ObjectStoreTest {
    abstract fun createObjectStore(): ObjectStore

    val store = createObjectStore()

    @Test
    fun `can persist and fetch an object`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readAllBytes()
            val channel = ByteChannel(autoFlush = true)
            channel.writeFully(image)
            channel.close()

            val result = store.persist(channel, image.size.toLong())
            result.bucket shouldBe InMemoryObjectStore.BUCKET

            val stream = ByteChannel(autoFlush = true)
            val fetchResult = store.fetch(result.bucket, result.key, stream)
            fetchResult.found shouldBe true
            fetchResult.contentLength shouldBe image.size.toLong()
            stream.toByteArray() shouldBe image
        }

    @Test
    fun `can fetch if the object does not exist`() =
        runTest {
            val stream = ByteChannel(autoFlush = true)
            val fetchResult = store.fetch("something", UUID.randomUUID().toString(), stream)

            fetchResult.found shouldBe false
            fetchResult.contentLength shouldBe 0
            stream.toByteArray() shouldHaveSize 0
        }

    @Test
    fun `can delete an object`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readAllBytes()
            val channel = ByteChannel(autoFlush = true)
            channel.writeFully(image)
            channel.close()
            val result = store.persist(channel, image.size.toLong())

            store.delete(result.bucket, result.key)

            val stream = ByteChannel(autoFlush = true)
            val fetchResult = store.fetch(result.bucket, result.key, stream)
            fetchResult.found shouldBe false
            fetchResult.contentLength shouldBe 0
            stream.toByteArray() shouldHaveSize 0
        }

    @Test
    fun `can delete if object does not exist`() =
        runTest {
            shouldNotThrowAny {
                store.delete("something", UUID.randomUUID().toString())
            }
        }

    @Test
    fun `deleteAll deletes supplied objects in bucket`() =
        runTest {
            val bytes1 = UUID.randomUUID().toString().toByteArray()
            val channel1 = ByteChannel(autoFlush = true)
            channel1.writeFully(bytes1)
            channel1.close()
            val result1 = store.persist(channel1, bytes1.size.toLong())

            val bytes2 = UUID.randomUUID().toString().toByteArray()
            val channel2 = ByteChannel(autoFlush = true)
            channel2.writeFully(bytes2)
            channel2.close()
            val result2 = store.persist(channel2, bytes2.size.toLong())

            val bytes3 = UUID.randomUUID().toString().toByteArray()
            val channel3 = ByteChannel(autoFlush = true)
            channel3.writeFully(bytes3)
            channel3.close()
            val result3 = store.persist(channel3, bytes3.size.toLong())

            result1.bucket shouldBe result2.bucket shouldBe result3.bucket
            store.deleteAll(result1.bucket, listOf(result1.key, result2.key, result3.key))

            var stream = ByteChannel(autoFlush = true)
            store.fetch(result1.bucket, result1.key, stream).apply {
                found shouldBe false
                contentLength shouldBe 0
                stream.toByteArray() shouldHaveSize 0
            }
            stream = ByteChannel(autoFlush = true)
            store.fetch(result2.bucket, result2.key, stream).apply {
                found shouldBe false
                contentLength shouldBe 0
                stream.toByteArray() shouldHaveSize 0
            }
            stream = ByteChannel(autoFlush = true)
            store.fetch(result3.bucket, result3.key, stream).apply {
                found shouldBe false
                contentLength shouldBe 0
                stream.toByteArray() shouldHaveSize 0
            }
        }

    @Test
    fun `deleteAll does nothing if wrong bucket is supplied`() =
        runTest {
            val bytes = UUID.randomUUID().toString().toByteArray()
            val channel = ByteChannel(autoFlush = true)
            channel.writeFully(bytes)
            channel.close()
            val result = store.persist(channel, bytes.size.toLong())

            store.deleteAll("somethingelse", listOf(result.key))

            val stream = ByteChannel(autoFlush = true)
            store.fetch(result.bucket, result.key, stream).apply {
                found shouldBe true
                contentLength shouldBe bytes.size
                stream.toByteArray() shouldHaveSize bytes.size
            }
        }

    @Test
    fun `can deleteAll if keys do not exist in bucket`() =
        runTest {
            val bytes = UUID.randomUUID().toString().toByteArray()
            val channel = ByteChannel(autoFlush = true)
            channel.writeFully(bytes)
            channel.close()
            val result = store.persist(channel, bytes.size.toLong())

            store.deleteAll(result.bucket, listOf(UUID.randomUUID().toString()))

            val stream = ByteChannel(autoFlush = true)
            store.fetch(result.bucket, result.key, stream).apply {
                found shouldBe true
                contentLength shouldBe bytes.size
                stream.toByteArray() shouldHaveSize bytes.size
            }
        }
}
