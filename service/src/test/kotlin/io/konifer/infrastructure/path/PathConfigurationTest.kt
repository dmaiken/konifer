package io.konifer.infrastructure.path

import io.konifer.common.image.ImageFormat
import io.konifer.domain.path.PathConfiguration
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PathConfigurationTest {
    @Test
    fun `all image formats are allowed by default`() {
        PathConfiguration().allowedFormats shouldBe ImageFormat.entries
    }
}
