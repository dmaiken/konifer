package io.konifer.domain.asset

import io.konifer.domain.context.InvalidPathException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AssetPathTest {
    @ParameterizedTest
    @ValueSource(strings = ["", "/"])
    fun `root aliases are canonical`(path: String) {
        AssetPath(path) shouldBe AssetPath.ROOT
        AssetPath(path).value shouldBe "/"
    }

    @ParameterizedTest
    @ValueSource(strings = ["users/123", "/users/123", "users/123/", "/users/123/"])
    fun `outer slashes are normalized`(path: String) {
        path.toAssetPath().value shouldBe "/users/123"
    }

    @ParameterizedTest
    @ValueSource(strings = ["//", "///", "//a", "a//", "/a//b", "/.", "/..", "/a/../b", "/a/./b", "/;", "/a b", "/é", "/%2F", "/a?b"])
    fun `malformed paths are rejected`(path: String) {
        shouldThrow<InvalidPathException> { AssetPath(path) }
    }

    @Test
    fun `case and permitted punctuation are preserved`() {
        val path = "/A_1/v1.2/~!$'()*+=@:-"
        AssetPath(path).value shouldBe path
    }

    @Test
    fun `descendants respect segment boundaries`() {
        val parent = AssetPath("/users/1")
        parent.isSelfOrDescendantOf(parent) shouldBe true
        AssetPath("/users/1/profile").isSelfOrDescendantOf(parent) shouldBe true
        AssetPath("/users/12").isSelfOrDescendantOf(parent) shouldBe false
        parent.isSelfOrDescendantOf(AssetPath.ROOT) shouldBe true
        AssetPath.ROOT.isSelfOrDescendantOf(parent) shouldBe false
    }
}
