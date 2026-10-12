package io.konifer.infrastructure.datastore.inmemory

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class InMemoryPathAdapterTest {
    @ParameterizedTest
    @ValueSource(strings = ["", "/"])
    fun `root paths share the same store key`(path: String) {
        InMemoryPathAdapter.toInMemoryPathFromUriPath(path) shouldBe "/"
    }

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
