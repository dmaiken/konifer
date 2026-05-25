package integration

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.image.ImageFormat

object ImageFactory {
    private const val STANDARD_PATH = "/images/joshua-tree/joshua-tree"
    private const val LARGE_PATH = "/images/large/large"

    fun testImage(
        format: ImageFormat = ImageFormat.JPEG,
        type: TestImageType = TestImageType.STANDARD,
    ): TestImage =
        when (type) {
            TestImageType.STANDARD -> {
                val bytes = javaClass.getResourceAsStream("$STANDARD_PATH${format.extension}")!!.readBytes()
                TestImage(
                    bytes = bytes,
                    attributes = bytes.toAttributes(format),
                )
            }
            TestImageType.LARGE -> {
                require(format == ImageFormat.JPEG) { "Large images are only supported in JPEG format" }
                val bytes = javaClass.getResourceAsStream("$LARGE_PATH${format.extension}")!!.readBytes()
                TestImage(
                    bytes = bytes,
                    attributes = bytes.toAttributes(format),
                )
            }
        }

    private fun ByteArray.toAttributes(format: ImageFormat): TestImageAttributes {
        var height: Int? = null
        var width: Int? = null
        Vips.run { arena ->
            val image = VImage.newFromBytes(arena, this)

            height = image.height
            width = image.width
        }

        return TestImageAttributes(
            height = checkNotNull(height),
            width = checkNotNull(width),
            format = format,
        )
    }
}

data class TestImage(
    val bytes: ByteArray,
    val attributes: TestImageAttributes,
)

data class TestImageAttributes(
    val height: Int,
    val width: Int,
    val format: ImageFormat,
)

enum class TestImageType {
    STANDARD,
    LARGE,
}
