package integration

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.client.KoniferResult
import io.konifer.common.image.ImageFormat
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID

class UploadRulesIntegrationTest : BaseIntegrationTest() {
    @ParameterizedTest
    @EnumSource(value = ImageFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["PIXELS"])
    fun `can store asset that passes upload rules`(format: ImageFormat) {
        runBlocking {
            val path = UUID.randomUUID().toString()
            val (image, attributes) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE, format = format)
            val storeResponse =
                client
                    .assets("/accept/$path")
                    .newAsset()
                    .fromBytes(
                        bytes = image,
                        format = format,
                    ).withAlt("image")
                    .withLabels(mapOf("key1" to "value1", "key2" to "value2"))
                    .withTags(setOf("tag1", "tag2"))
                    .store()
            storeResponse::class shouldBe KoniferResult.Success::class
            val fetchResponse =
                client
                    .assets("/accept/$path")
                    .originalVariant()
                    .fetchContentBytes()
            fetchResponse::class shouldBe KoniferResult.Success::class
            val content = (fetchResponse as KoniferResult.Success).value
            tika.detect(content) shouldBe format.mimeType

            Vips.run { arena ->
                val vImage = VImage.newFromBytes(arena, content)
                vImage.height shouldBe attributes.height
                vImage.width shouldBe attributes.width
            }
        }
    }
}
