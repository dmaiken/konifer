package io.konifer.infrastructure.path

import io.konifer.domain.path.CacheControlProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CacheControlPropertiesTest {
    @Test
    fun `cannot create with negative maxAge`() {
        shouldThrow<IllegalArgumentException> {
            CacheControlProperties(
                maxAge = -1,
                enabled = true,
                sharedMaxAge = null,
                visibility = null,
                revalidate = null,
                staleWhileRevalidate = null,
                staleIfError = null,
                immutable = null,
            )
        }.message shouldBe "Max age must be positive"
    }

    @Test
    fun `cannot create with negative sharedMaxAge`() {
        shouldThrow<IllegalArgumentException> {
            CacheControlProperties(
                maxAge = null,
                enabled = true,
                sharedMaxAge = -1,
                visibility = null,
                revalidate = null,
                staleWhileRevalidate = null,
                staleIfError = null,
                immutable = null,
            )
        }.message shouldBe "Shared max age must be positive"
    }

    @Test
    fun `cannot create with negative staleWhileRevalidate`() {
        shouldThrow<IllegalArgumentException> {
            CacheControlProperties(
                maxAge = null,
                enabled = true,
                sharedMaxAge = null,
                visibility = null,
                revalidate = null,
                staleWhileRevalidate = -1,
                staleIfError = null,
                immutable = null,
            )
        }.message shouldBe "Stale while revalidate must be positive"
    }

    @Test
    fun `cannot create with negative staleIfError`() {
        shouldThrow<IllegalArgumentException> {
            CacheControlProperties(
                maxAge = null,
                enabled = true,
                sharedMaxAge = null,
                visibility = null,
                revalidate = null,
                staleWhileRevalidate = null,
                staleIfError = -1,
                immutable = null,
            )
        }.message shouldBe "Stale if error must be positive"
    }
}
