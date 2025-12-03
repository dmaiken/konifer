package io.direkt

import io.WildcardRegexAdapter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class WildcardRegexAdapterTest {
    val wildcardsRegexAdapter = WildcardRegexAdapter()

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/assets/user1/profile-picture/",
            "/assets/user1/profile-picture",
        ],
    )
    fun `toRegex converts path without any wildcards correctly`(uriPath: String) {
        val regex = wildcardsRegexAdapter.toRegex(uriPath)

        regex.matches("/assets/user1/profile-picture") shouldBe true
        regex.matches("/assets/user1/profile-picture/") shouldBe true
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/assets/*/profile-picture/",
            "/assets/*/profile-picture",
        ],
    )
    fun `toRegex converts path with single wildcards correctly`(uriPath: String) {
        val regex = wildcardsRegexAdapter.toRegex(uriPath)

        regex.matches("/assets/user1/profile-picture/") shouldBe true
        regex.matches("/assets/9000/profile-picture") shouldBe true
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/assets/**/profile-picture/",
            "/assets/**/profile-picture",
        ],
    )
    fun `toRegex converts path with double wildcards in the middle correctly`(uriPath: String) {
        val regex = wildcardsRegexAdapter.toRegex(uriPath)

        regex.matches("/assets/user1/somethingElse/profile-picture/") shouldBe true
        regex.matches("/assets/9000/somethingElse/profile-picture") shouldBe true
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/assets/user/**",
            "/assets/user/**/",
        ],
    )
    fun `toRegex converts path with double wildcards at the end correctly`(uriPath: String) {
        val regex = wildcardsRegexAdapter.toRegex(uriPath)

        regex.matches("/assets/user/somethingElse/profile-picture/") shouldBe true
        regex.matches("/assets/user/somethingElse/profile-picture") shouldBe true
    }
}
