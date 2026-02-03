package io.konifer.infrastructure.http.cache

import io.konifer.domain.path.CacheControlProperties
import io.konifer.domain.path.CacheControlRevalidate
import io.konifer.domain.path.CacheControlVisibility
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class CacheControlHeaderFactoryTest {
    @Test
    fun `can construct header with max-age`() {
        CacheControlHeaderFactory.constructHeader(
            CacheControlProperties.default.copy(
                enabled = true,
                maxAge = 100,
            ),
        ) shouldBe "max-age=100"
    }

    @Test
    fun `can construct header with s-maxage`() {
        CacheControlHeaderFactory.constructHeader(
            CacheControlProperties.default.copy(
                enabled = true,
                sharedMaxAge = 100,
            ),
        ) shouldBe "s-maxage=100"
    }

    @ParameterizedTest
    @EnumSource(CacheControlVisibility::class)
    fun `can construct header with visibility`(visibility: CacheControlVisibility) {
        CacheControlHeaderFactory.constructHeader(
            CacheControlProperties.default.copy(
                enabled = true,
                visibility = visibility,
            ),
        ) shouldBe visibility.value
    }

    @ParameterizedTest
    @EnumSource(CacheControlRevalidate::class)
    fun `can construct header with revalidate`(revalidate: CacheControlRevalidate) {
        CacheControlHeaderFactory.constructHeader(
            CacheControlProperties.default.copy(
                enabled = true,
                revalidate = revalidate,
            ),
        ) shouldBe revalidate.value
    }

    @Test
    fun `can construct header with stale-while-revalidate`() {
        CacheControlHeaderFactory.constructHeader(
            CacheControlProperties.default.copy(
                enabled = true,
                staleWhileRevalidate = 100,
            ),
        ) shouldBe "stale-while-revalidate=100"
    }

    @Test
    fun `can construct header with stale-if-error`() {
        CacheControlHeaderFactory.constructHeader(
            CacheControlProperties.default.copy(
                enabled = true,
                staleIfError = 100,
            ),
        ) shouldBe "stale-if-error=100"
    }

    @Test
    fun `can construct header with immutable`() {
        CacheControlHeaderFactory.constructHeader(
            CacheControlProperties.default.copy(
                enabled = true,
                immutable = true,
            ),
        ) shouldBe "immutable"
    }

    @Test
    fun `can construct header with all directives`() {
        CacheControlHeaderFactory.constructHeader(
            CacheControlProperties(
                enabled = true,
                maxAge = 100,
                sharedMaxAge = 200,
                visibility = CacheControlVisibility.PUBLIC,
                revalidate = CacheControlRevalidate.MUST_REVALIDATE,
                staleWhileRevalidate = 500,
                staleIfError = 600,
                immutable = true,
            ),
        ) shouldBe "max-age=100, s-maxage=200, public, must-revalidate, stale-while-revalidate=500, stale-if-error=600, immutable"
    }
}
