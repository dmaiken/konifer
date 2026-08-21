package app.photofox.vipsffm

import app.photofox.vipsffm.enums.VipsAccess
import io.konifer.ImageFactory
import io.konifer.TestImageType
import io.konifer.common.image.ImageFormat
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_ACCESS
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_N_PAGES
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_PAGE_HEIGHT
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VipsImageCopyMemoryTest {
    @Test
    fun `materializes a sequential multipage image and preserves its header`() {
        val encoded =
            ImageFactory
                .testImage(
                    format = ImageFormat.GIF,
                    type = TestImageType.KERMIT,
                ).bytes

        Vips.run { arena ->
            val source =
                VImage.newFromBytes(
                    arena,
                    encoded,
                    VipsOption.Int("n", -1),
                    VipsOption.Enum(OPTION_ACCESS, VipsAccess.ACCESS_SEQUENTIAL),
                )

            val copied = VipsImageCopyMemory.copyMemory(arena, source)

            copied.width shouldBe source.width
            copied.height shouldBe source.height
            copied.getInt(OPTION_N_PAGES) shouldBe source.getInt(OPTION_N_PAGES)
            copied.getInt(OPTION_PAGE_HEIGHT) shouldBe source.getInt(OPTION_PAGE_HEIGHT)

            // Demand the bottom before the top to verify the copy is no longer
            // constrained by the sequential loader's read order.
            copied.extractArea(0, copied.height - 1, copied.width, 1).writeToMemory()
            copied.extractArea(0, 0, copied.width, 1).writeToMemory()
        }
    }
}
