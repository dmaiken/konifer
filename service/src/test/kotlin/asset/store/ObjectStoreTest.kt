package asset.store

import asset.store.InMemoryObjectStore.Companion.BUCKET
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID

abstract class ObjectStoreTest {
    abstract fun createObjectStore(): ObjectStore

    val store = createObjectStore()

    @Test
    fun `can persist and fetch an object`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readAllBytes()
            val channel = ByteChannel(autoFlush = true)
            val resultDeferred =
                async {
                    store.persist(channel, image.size.toLong())
                }
            channel.writeFully(image)
            channel.close()

            val result = resultDeferred.await()
            result.bucket shouldBe InMemoryObjectStore.BUCKET

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            val fetchResult = store.fetch(result.bucket, result.key, stream)
            fetchResult.found shouldBe true
            fetchResult.contentLength shouldBe image.size.toLong()
            fetched.await() shouldBe image
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
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readAllBytes()
            val channel = ByteChannel(autoFlush = true)
            val resultDeferred =
                async {
                    store.persist(channel, image.size.toLong())
                }
            channel.writeFully(image)
            channel.close()
            val result = resultDeferred.await()

            store.delete(result.bucket, result.key)

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            val fetchResult = store.fetch(result.bucket, result.key, stream)
            fetchResult.found shouldBe false
            fetchResult.contentLength shouldBe 0
            fetched.await() shouldHaveSize 0
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
            val result1Deferred =
                async {
                    store.persist(channel1, bytes1.size.toLong())
                }
            channel1.writeFully(bytes1)
            channel1.close()
            val result1 = result1Deferred.await()

            val bytes2 = UUID.randomUUID().toString().toByteArray()
            val channel2 = ByteChannel(autoFlush = true)
            val result2Deferred =
                async {
                    store.persist(channel2, bytes2.size.toLong())
                }
            channel2.writeFully(bytes1)
            channel2.close()
            val result2 = result2Deferred.await()

            val bytes3 = UUID.randomUUID().toString().toByteArray()
            val channel3 = ByteChannel(autoFlush = true)
            val result3Deferred =
                async {
                    store.persist(channel3, bytes3.size.toLong())
                }
            channel3.writeFully(bytes1)
            channel3.close()
            val result3 = result3Deferred.await()

            result1.bucket shouldBe result2.bucket shouldBe result3.bucket
            store.deleteAll(result1.bucket, listOf(result1.key, result2.key, result3.key))

            var stream = ByteChannel(autoFlush = true)
            val fetched1 =
                async {
                    stream.toByteArray()
                }
            store.fetch(result1.bucket, result1.key, stream).apply {
                found shouldBe false
                contentLength shouldBe 0
                fetched1.await() shouldHaveSize 0
            }
            stream = ByteChannel(autoFlush = true)
            val fetched2 =
                async {
                    stream.toByteArray()
                }
            store.fetch(result2.bucket, result2.key, stream).apply {
                found shouldBe false
                contentLength shouldBe 0
                fetched2.await() shouldHaveSize 0
            }
            stream = ByteChannel(autoFlush = true)
            val fetched3 =
                async {
                    stream.toByteArray()
                }
            store.fetch(result3.bucket, result3.key, stream).apply {
                found shouldBe false
                contentLength shouldBe 0
                fetched3.await() shouldHaveSize 0
            }
        }

    @Test
    fun `deleteAll does nothing if wrong bucket is supplied`() =
        runTest {
            val bytes = UUID.randomUUID().toString().toByteArray()
            val channel = ByteChannel(autoFlush = true)
            val result1Deferred =
                async {
                    store.persist(channel, bytes.size.toLong())
                }
            channel.writeFully(bytes)
            channel.close()
            val result = result1Deferred.await()

            store.deleteAll("somethingelse", listOf(result.key))

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            store.fetch(result.bucket, result.key, stream).apply {
                found shouldBe true
                contentLength shouldBe bytes.size
                fetched.await() shouldHaveSize bytes.size
            }
        }

    @Test
    fun `can deleteAll if keys do not exist in bucket`() =
        runTest {
            val bytes = UUID.randomUUID().toString().toByteArray()
            val channel = ByteChannel(autoFlush = true)
            val result1Deferred =
                async {
                    store.persist(channel, bytes.size.toLong())
                }
            channel.writeFully(bytes)
            channel.close()
            val result = result1Deferred.await()

            store.deleteAll(result.bucket, listOf(UUID.randomUUID().toString()))

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            store.fetch(result.bucket, result.key, stream).apply {
                found shouldBe true
                contentLength shouldBe bytes.size
                fetched.await() shouldHaveSize bytes.size
            }
        }

    @Test
    fun `exists returns true if the object exists in the object store`() =
        runTest {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readAllBytes()
            val channel = ByteChannel(autoFlush = true)
            val resultDeferred =
                async {
                    store.persist(channel, image.size.toLong())
                }
            channel.writeFully(image)
            channel.close()

            val result = resultDeferred.await()

            store.exists(result.bucket, result.key) shouldBe true
        }

    @Test
    fun `exists returns false if the bucket and key does not exist in the object store`() =
        runTest {
            store.exists(UUID.randomUUID().toString(), UUID.randomUUID().toString()) shouldBe false
        }

    @Test
    fun `exists returns false if the key does not exist in the object store`() =
        runTest {
            store.exists(BUCKET, UUID.randomUUID().toString()) shouldBe false
        }
}
