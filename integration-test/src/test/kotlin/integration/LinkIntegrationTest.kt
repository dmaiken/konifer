package integration

import io.konifer.clientV2.KoniferV2Result
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
                clientV2
                    .asset(path)
                    .newAsset()
                    .fromBytes(
                        bytes = image,
                        format = attributes.format,
                    ).withAlt("image")
                    .withLabels(mapOf("key1" to "value1", "key2" to "value2"))
                    .withTags(setOf("tag1", "tag2"))
                    .store()
            storeResponse::class shouldBe KoniferV2Result.Success::class

            val linkResponse =
                clientV2
                    .asset(path)
                    .originalVariant()
                    .link()
            val receivedAt = Clock.System.now()

            linkResponse::class shouldBe KoniferV2Result.Success::class
            with(linkResponse as KoniferV2Result.Success) {
                shouldNotThrowAny { Url(value.url) }

                val expiresAt = requireNotNull(value.expiresAt).toInstant(TimeZone.UTC)
                expiresAt shouldBeIn (requestedAt + 30.minutes)..(receivedAt + 30.minutes)
            }
        }
    }
}
