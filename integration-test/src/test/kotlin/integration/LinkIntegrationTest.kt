package integration

import io.konifer.client.KoniferResponse
import io.konifer.common.http.StoreAssetRequest
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
                client.storeAsset(
                    path = path,
                    format = attributes.format,
                    bytes = image,
                    request =
                        StoreAssetRequest(
                            alt = "image",
                            tags = setOf("tag1", "tag2"),
                            labels = mapOf("key1" to "value1", "key2" to "value2"),
                        ),
                )
            storeResponse::class shouldBe KoniferResponse.Success::class

            val linkResponse =
                client.fetchAssetLink(
                    path = path,
                )
            val receivedAt = Clock.System.now()

            linkResponse::class shouldBe KoniferResponse.Success::class
            with(linkResponse as KoniferResponse.Success) {
                shouldNotThrowAny { Url(body.url) }

                val expiresAt = requireNotNull(body.expiresAt).toInstant(TimeZone.UTC)
                expiresAt shouldBeIn (requestedAt + 30.minutes)..(receivedAt + 30.minutes)
            }
        }
    }
}
