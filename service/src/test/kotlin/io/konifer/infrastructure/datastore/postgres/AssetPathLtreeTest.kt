package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.asset.AssetPath
import io.konifer.domain.context.InvalidPathException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.jooq.postgres.extensions.types.Ltree
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AssetPathLtreeTest {
    @ParameterizedTest
    @ValueSource(strings = ["/", "/user1/profile-picture", "/root/rooted", "/A_1/.~!$'()*+=@:-", "/a.b", "/a/b", "/a_2eb", "/__", "/v1.2"])
    fun `paths round trip through ltree`(value: String) {
        val path = AssetPath(value)
        path.toLtree().toAssetPath() shouldBe path
    }

    @Test
    fun `encoding is readable and unambiguous`() {
        AssetPath.ROOT.toLtree().data() shouldBe "root_v2"
        AssetPath("/users/alice@example.com").toLtree().data() shouldBe "root_v2.users.alice_40example_2ecom"
        AssetPath("/images/profile_picture").toLtree().data() shouldBe "root_v2.images.profile__picture"
        AssetPath("/A_1/.~!$'()*+=@:-").toLtree().data() shouldBe
            "root_v2.A__1._2e_7e_21_24_27_28_29_2a_2b_3d_40_3a-"
        AssetPath("/a.b").toLtree().data() shouldBe "root_v2.a_2eb"
        AssetPath("/a/b").toLtree().data() shouldBe "root_v2.a.b"
        AssetPath("/a_2eb").toLtree().data() shouldBe "root_v2.a__2eb"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "root", "root.a", "root_v2other.a", "root_v2.", "root_v2..a",
            "root_v2.a_", "root_v2.a_2", "root_v2.a_zz", "root_v2.a_2E",
            "root_v2.a_61", "root_v2.a_5f", "root_v2.a_2f", "root_v2.a_25", "root_v2.a_20",
            "root_v2.a_00", "root_v2.a_7f", "root_v2.a_3b", "root_v2.a@b",
        ],
    )
    fun `invalid storage representations are rejected`(value: String) {
        shouldThrow<IllegalArgumentException> { Ltree.valueOf(value).toAssetPath() }
    }

    @ParameterizedTest
    @ValueSource(strings = ["root_v2._2e", "root_v2._2e_2e"])
    fun `decoded paths still satisfy domain invariants`(value: String) {
        shouldThrow<InvalidPathException> { Ltree.valueOf(value).toAssetPath() }
    }

    @Test
    fun `encoded label limits account for expansion`() {
        listOf("a".repeat(1000), "_".repeat(500), ".".repeat(333) + "a").forEach { segment ->
            val path = AssetPath("/$segment")
            path.toLtree().toAssetPath() shouldBe path
        }
        listOf("a".repeat(1001), "_".repeat(501), ".".repeat(333) + "aa").forEach { segment ->
            shouldThrow<InvalidPathException> { AssetPath("/$segment").toLtree() }
        }
        shouldThrow<IllegalArgumentException> { Ltree.valueOf("root_v2." + "a".repeat(1001)).toAssetPath() }
    }
}
