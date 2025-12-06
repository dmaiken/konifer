package io.direkt.infrastructure.`object`

import io.direkt.domain.ports.ObjectRepository
import io.direkt.getResourceAsFile
import io.direkt.domain.image.ImageFormat
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID

abstract class ObjectRepositoryTest {
    abstract fun createObjectStore(): ObjectRepository

    protected val store = createObjectStore()

    companion object {
        protected const val BUCKET_1 = "bucket-1"
        protected const val BUCKET_2 = "bucket-2"
        protected const val BUCKET_3 = "bucket-3"
    }

    @Test
    fun `can persist and fetch an object`() =
        runTest {
            val image = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
            val result = store.persist(BUCKET_1, image, ImageFormat.PNG)
            result.bucket shouldBe BUCKET_1
            result.key shouldEndWith ImageFormat.PNG.extension

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            val fetchResult = store.fetch(result.bucket, result.key, stream)
            fetchResult.found shouldBe true
            fetchResult.contentLength shouldBe image.readBytes().size.toLong()
            fetched.await() shouldBe image.readBytes()
        }

    @Test
    fun `can persist and fetch an object without supplying content length`() =
        runTest {
            val image = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
            val result = store.persist(BUCKET_1, image, ImageFormat.PNG)
            result.bucket shouldBe BUCKET_1

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            val fetchResult = store.fetch(result.bucket, result.key, stream)
            fetchResult.found shouldBe true
            fetchResult.contentLength shouldBe image.readBytes().size.toLong()
            fetched.await() shouldBe image.readBytes()
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
            val image = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
            val result = store.persist(BUCKET_1, image, ImageFormat.PNG)

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
            val image1 = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
            val result1 = store.persist(BUCKET_1, image1, ImageFormat.PNG)

            val image2 = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.jpeg")
            val result2 = store.persist(BUCKET_1, image2, ImageFormat.JPEG)

            val image3 = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.heic")
            val result3 = store.persist(BUCKET_1, image3, ImageFormat.HEIC)

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
            val image = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
            val result = store.persist(BUCKET_1, image, ImageFormat.PNG)

            store.deleteAll(BUCKET_2, listOf(result.key))

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            store.fetch(result.bucket, result.key, stream).apply {
                found shouldBe true
                contentLength shouldBe image.readBytes().size.toLong()
                fetched.await() shouldBe image.readBytes()
            }
        }

    @Test
    fun `can deleteAll if keys do not exist in bucket`() =
        runTest {
            val image = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
            val result = store.persist(BUCKET_1, image, ImageFormat.PNG)

            store.deleteAll(result.bucket, listOf(UUID.randomUUID().toString()))

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            store.fetch(result.bucket, result.key, stream).apply {
                found shouldBe true
                contentLength shouldBe image.readBytes().size.toLong()
                fetched.await() shouldBe image.readBytes()
            }
        }

    @Test
    fun `exists returns true if the object exists in the object store`() =
        runTest {
            val image = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
            val result = store.persist(BUCKET_1, image, ImageFormat.PNG)

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
            store.exists(BUCKET_1, UUID.randomUUID().toString()) shouldBe false
        }
}
