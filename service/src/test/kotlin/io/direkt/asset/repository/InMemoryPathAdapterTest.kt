package io.direkt.asset.repository

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InMemoryPathAdapterTest {
    @Test
    fun `trailing slash is stripped`() {
        val inMemoryPath = InMemoryPathAdapter.toInMemoryPathFromUriPath("/profile-picture/")

        inMemoryPath shouldBe "/profile-picture"
    }
}
