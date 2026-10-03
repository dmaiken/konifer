package integration

import io.konifer.client.KoniferResult
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class LinkIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `link returns proper expiresAt when returning presigned url`() {
        runBlocking {
            val requestedAt = Clock.System.now()
            val path = "presigned/${UUID.randomUUID()}"
            val (image, attributes) = ImageFactory.testImage()
            val storeResponse =
                client
                    .assets(path)
                    .newAsset()
                    .fromBytes(
                        bytes = image,
                        format = attributes.format,
                    ).withAlt("image")
                    .withLabels(mapOf("key1" to "value1", "key2" to "value2"))
                    .withTags(setOf("tag1", "tag2"))
                    .store()
            storeResponse::class shouldBe KoniferResult.Success::class

            val linkResponse =
                client
                    .assets(path)
                    .originalVariant()
                    .fetchLink()
            val receivedAt = Clock.System.now()

            linkResponse::class shouldBe KoniferResult.Success::class
            with(linkResponse as KoniferResult.Success) {
                shouldNotThrowAny { Url(value.url) }

                val expiresAt = requireNotNull(value.expiresAt).toInstant(TimeZone.UTC)
                expiresAt shouldBeIn (requestedAt + 30.minutes)..(receivedAt + 30.minutes)
            }
        }
    }
}
