package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.asset.AssetPath
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.jooq.postgres.extensions.types.Ltree
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AssetPathLtreeTest {
    @ParameterizedTest
    @ValueSource(strings = ["/", "/user1/profile-picture", "/root/rooted", "/A_1/~!$'()*+=@-"])
    fun `paths round trip through ltree`(value: String) {
        val path = AssetPath(value)
        path.toLtree().toAssetPath() shouldBe path
    }

    @Test
    fun `storage representation is unchanged`() {
        AssetPath.ROOT.toLtree().data() shouldBe "root"
        AssetPath("/user1/profile-picture/").toLtree().data() shouldBe "root.user1.profile-picture"
    }

    @Test
    fun `only the exact root prefix is accepted`() {
        shouldThrow<IllegalArgumentException> { Ltree.valueOf("rooted.user").toAssetPath() }
    }
}
