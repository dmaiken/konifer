package io.konifer.infrastructure.objectstore

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.path.RedirectProperties
import io.konifer.domain.path.RedirectStrategy
import io.konifer.domain.path.TemplateProperties
import io.konifer.domain.ports.ObjectStore
import io.konifer.getResourceAsFile
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

abstract class ObjectStoreTest {
    abstract fun createObjectStore(): ObjectStore

    companion object {
        protected const val BUCKET_1 = "bucket-1"
        protected const val BUCKET_2 = "bucket-2"
    }

    protected lateinit var store: ObjectStore

    @BeforeEach
    fun initialize() {
        store = createObjectStore()
    }

    private val image =
        runBlocking {
            javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
        }

    // 2. Create a factory function for a fresh channel
    private fun getFreshImageChannel(format: ImageFormat = ImageFormat.PNG): ByteReadChannel {
        // toByteReadChannel() runs asynchronously and doesn't block
        return javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.toByteReadChannel()
    }

    @Test
    fun `can persist and fetch an object`() =
        runTest {
            val key = "${UuidCreator.getRandomBasedFast()}${ImageFormat.PNG.extension}"

            val channel = ByteChannel()
            launch {
                getFreshImageChannel().copyTo(channel)
                channel.close()
            }
            val result = store.persist(BUCKET_1, key, channel)
            result.toLocalDate() shouldBe LocalDate.now()

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }

            val fetchResult = store.fetch(BUCKET_1, key, stream)
            fetchResult.found shouldBe true
            fetchResult.contentLength shouldBe image.length()
            fetched.await() shouldBe image.readBytes()
        }

    @Test
    fun `can persist and fetch an object without supplying content length`() =
        runTest {
            val key = "${UuidCreator.getRandomBasedFast()}${ImageFormat.PNG.extension}"
            val channel = ByteChannel()
            launch {
                getFreshImageChannel().copyTo(channel)
                channel.close()
            }
            store.persist(BUCKET_1, key, channel)

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            val fetchResult = store.fetch(BUCKET_1, key, stream)
            fetchResult.found shouldBe true
            fetchResult.contentLength shouldBe image.readBytes().size.toLong()
            fetched.await() shouldBe image.readBytes()
        }

    @Test
    fun `can fetch if the object does not exist`() =
        runTest {
            val stream = ByteChannel(autoFlush = true)
            val fetchResult = store.fetch(BUCKET_1, UuidCreator.getRandomBasedFast().toString(), stream)

            fetchResult.found shouldBe false
            fetchResult.contentLength shouldBe 0
            stream.toByteArray() shouldHaveSize 0
        }

    @Test
    fun `can delete an object`() =
        runTest {
            val key = "${UuidCreator.getRandomBasedFast()}${ImageFormat.PNG.extension}"
            val channel = ByteChannel()
            launch {
                getFreshImageChannel().copyTo(channel)
                channel.close()
            }
            store.persist(BUCKET_1, key, channel)
            store.delete(BUCKET_1, key)

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            val fetchResult = store.fetch(BUCKET_1, key, stream)
            fetchResult.found shouldBe false
            fetchResult.contentLength shouldBe 0
            fetched.await() shouldHaveSize 0
        }

    @Test
    fun `can delete if object does not exist`() =
        runTest {
            shouldNotThrowAny {
                store.delete(BUCKET_1, UuidCreator.getRandomBasedFast().toString())
            }
        }

    @Test
    fun `deleteAll deletes supplied objects in bucket`() =
        runTest {
            val key1 = "${UuidCreator.getRandomBasedFast()}${ImageFormat.PNG.extension}"
            val channel1 = ByteChannel()
            launch {
                getFreshImageChannel().copyTo(channel1)
                channel1.close()
            }
            store.persist(BUCKET_1, key1, channel1)

            val key2 = "${UuidCreator.getRandomBasedFast()}${ImageFormat.JPEG.extension}"
            val channel2 = ByteChannel()
            launch {
                getFreshImageChannel(ImageFormat.JPEG).copyTo(channel2)
                channel2.close()
            }
            store.persist(BUCKET_1, key2, channel2)

            val key3 = "${UuidCreator.getRandomBasedFast()}${ImageFormat.HEIC.extension}"
            val channel3 = ByteChannel()
            launch {
                getFreshImageChannel(ImageFormat.HEIC).copyTo(channel3)
                channel3.close()
            }
            store.persist(BUCKET_1, key3, channel3)

            store.deleteAll(BUCKET_1, listOf(key1, key2, key3))

            var stream = ByteChannel(autoFlush = true)
            val fetched1 =
                async {
                    stream.toByteArray()
                }
            store.fetch(BUCKET_1, key1, stream).apply {
                found shouldBe false
                contentLength shouldBe 0
                fetched1.await() shouldHaveSize 0
            }
            stream = ByteChannel(autoFlush = true)
            val fetched2 =
                async {
                    stream.toByteArray()
                }
            store.fetch(BUCKET_1, key2, stream).apply {
                found shouldBe false
                contentLength shouldBe 0
                fetched2.await() shouldHaveSize 0
            }
            stream = ByteChannel(autoFlush = true)
            val fetched3 =
                async {
                    stream.toByteArray()
                }
            store.fetch(BUCKET_1, key3, stream).apply {
                found shouldBe false
                contentLength shouldBe 0
                fetched3.await() shouldHaveSize 0
            }
        }

    @Test
    fun `deleteAll does nothing if wrong bucket is supplied`() =
        runTest {
            val key = "${UuidCreator.getRandomBasedFast()}${ImageFormat.PNG.extension}"
            val channel = ByteChannel()
            launch {
                getFreshImageChannel().copyTo(channel)
                channel.close()
            }
            store.persist(BUCKET_1, key, channel)

            store.deleteAll(BUCKET_2, listOf(key))

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            store.fetch(BUCKET_1, key, stream).apply {
                found shouldBe true
                contentLength shouldBe image.readBytes().size.toLong()
                fetched.await() shouldBe image.readBytes()
            }
        }

    @Test
    fun `can deleteAll if keys do not exist in bucket`() =
        runTest {
            val key = "${UuidCreator.getRandomBasedFast()}${ImageFormat.PNG.extension}"
            val channel = ByteChannel()
            launch {
                getFreshImageChannel().copyTo(channel)
                channel.close()
            }
            store.persist(BUCKET_1, key, channel)

            store.deleteAll(BUCKET_1, listOf(UuidCreator.getRandomBasedFast().toString()))

            val stream = ByteChannel(autoFlush = true)
            val fetched =
                async {
                    stream.toByteArray()
                }
            store.fetch(BUCKET_1, key, stream).apply {
                found shouldBe true
                contentLength shouldBe image.readBytes().size.toLong()
                fetched.await() shouldBe image.readBytes()
            }
        }

    @Test
    fun `exists returns true if the object exists in the object store`() =
        runTest {
            val key = "${UuidCreator.getRandomBasedFast()}${ImageFormat.PNG.extension}"
            val channel = ByteChannel()
            launch {
                getFreshImageChannel().copyTo(channel)
                channel.close()
            }
            store.persist(BUCKET_1, key, channel)

            store.exists(BUCKET_1, key) shouldBe true
        }

    @Test
    fun `exists returns false if the bucket and key does not exist in the object store`() =
        runTest {
            store.exists(UuidCreator.getRandomBasedFast().toString(), UuidCreator.getRandomBasedFast().toString()) shouldBe false
        }

    @Test
    fun `exists returns false if the key does not exist in the object store`() =
        runTest {
            store.exists(BUCKET_1, UuidCreator.getRandomBasedFast().toString()) shouldBe false
        }

    @Test
    fun `returns no url if redirect mode is disabled`() =
        runTest {
            store.generateObjectUrl(
                bucket = BUCKET_1,
                key = UuidCreator.getRandomBasedFast().toString(),
                properties =
                    RedirectProperties(
                        strategy = RedirectStrategy.NONE,
                    ),
            ) shouldBe null
        }

    @Test
    fun `can create templated url`() =
        runTest {
            val bucket = "bucket"
            val key = UuidCreator.getRandomBasedFast().toString()

            val properties =
                RedirectProperties(
                    strategy = RedirectStrategy.TEMPLATE,
                    template =
                        TemplateProperties(
                            string = "https://localhost:9000/{bucket}/{key}",
                        ),
                )
            val url = store.generateObjectUrl(bucket, key, properties)
            url shouldBe "https://localhost:9000/$bucket/$key"
        }
}
