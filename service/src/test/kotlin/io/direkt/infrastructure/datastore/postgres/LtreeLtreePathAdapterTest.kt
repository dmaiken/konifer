package io.direkt.infrastructure.datastore.postgres

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LtreeLtreePathAdapterTest {
    @ParameterizedTest
    @ValueSource(strings = ["", "/"])
    fun `root path is used when uri path is the root`(uriPath: String) {
        val treePath = LtreePathAdapter.toTreePathFromUriPath(uriPath)

        treePath.data() shouldBe LtreePathAdapter.TREE_ROOT
    }

    @ParameterizedTest
    @ValueSource(strings = ["/;", "/:", "/.", "/profile-picture/1.2.3"])
    fun `path is rejected if not valid`(uriPath: String) {
        shouldThrow<IllegalArgumentException> {
            LtreePathAdapter.toTreePathFromUriPath(uriPath)
        }
    }

    @Test
    fun `generates the tree path correctly`() {
        val uriPath = "/user1/profile-picture/"

        val treePath = LtreePathAdapter.toTreePathFromUriPath(uriPath)

        treePath.data() shouldBe "${LtreePathAdapter.TREE_ROOT}.user1.profile-picture"
    }

    @Test
    fun `toUriPath converts tree path back into uri path`() {
        val treePath = "${LtreePathAdapter.TREE_ROOT}.user1.profile-picture"

        val uriPath = LtreePathAdapter.toUriPath(treePath)

        uriPath shouldBe "/user1/profile-picture"
    }
}
