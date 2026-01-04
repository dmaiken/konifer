package io.direkt.infrastructure.datastore.inmemory

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InMemoryPathAdapterTest {
    @Test
    fun `trailing slash is stripped`() {
        val inMemoryPath = InMemoryPathAdapter.toInMemoryPathFromUriPath("/profile-picture/")

        inMemoryPath shouldBe "/profile-picture"
    }

    @Test
    fun `prefix slash is added if not already there`() {
        val inMemoryPath = InMemoryPathAdapter.toInMemoryPathFromUriPath("profile-picture/")

        inMemoryPath shouldBe "/profile-picture"
    }
}
