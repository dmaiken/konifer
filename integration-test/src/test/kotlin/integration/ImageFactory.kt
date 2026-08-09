package integration

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import io.konifer.common.image.ImageFormat

object ImageFactory {
    private const val STANDARD_PATH = "/images/joshua-tree/joshua-tree"
    private const val LARGE_PATH = "/images/large/large"
    private const val KERMIT_PATH = "/images/kermit/kermit"

    fun testImage(
        format: ImageFormat = ImageFormat.JPEG,
        type: TestImageType = TestImageType.JOSHUA_TREE,
    ): TestImage =
        when (type) {
            TestImageType.JOSHUA_TREE -> {
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
            TestImageType.KERMIT -> {
                require(format in listOf(ImageFormat.GIF, ImageFormat.WEBP)) { "Kermit images are only supported in animated formats" }
                val bytes = javaClass.getResourceAsStream("$KERMIT_PATH${format.extension}")!!.readBytes()
                TestImage(
                    bytes = bytes,
                    attributes = bytes.toAttributes(format),
                )
            }
        }

    private fun ByteArray.toAttributes(format: ImageFormat): TestImageAttributes {
        var height: Int? = null
        var width: Int? = null
        var pages: Int? = null
        Vips.run { arena ->
            // Load image with all pages
            val image = VImage.newFromBytes(arena, this, VipsOption.Int("n", -1))

            height = image.height
            width = image.width
            pages = image.getInt("n-pages")
        }

        return TestImageAttributes(
            height = checkNotNull(height),
            width = checkNotNull(width),
            pages = checkNotNull(pages),
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
    val pages: Int,
)

enum class TestImageType {
    JOSHUA_TREE,
    LARGE,
    KERMIT,
}
