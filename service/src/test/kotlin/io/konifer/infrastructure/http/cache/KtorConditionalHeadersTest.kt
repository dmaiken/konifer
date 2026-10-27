package io.konifer.infrastructure.http.cache

import io.konifer.domain.variant.VariantId
import io.konifer.infrastructure.http.CustomAttributes.lastModifiedKey
import io.konifer.infrastructure.http.CustomAttributes.variantIdKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class KtorConditionalHeadersTest {
    @Test
    fun `subsecond metadata changes invalidate ETags despite identical HTTP dates`() =
        testApplication {
            val variantId = VariantId()
            val modifiedAt = AtomicReference(Instant.parse("2026-10-24T14:30:00.000000001Z"))
            application {
                configureConditionalHeaders()
                routing {
                    get("/content") {
                        call.attributes[variantIdKey] = variantId
                        call.attributes[lastModifiedKey] = modifiedAt.get()
                        call.respondBytes(byteArrayOf(1, 2, 3), ContentType.Image.PNG)
                    }
                }
            }
            val initial = client.get("/content")
            initial.status shouldBe HttpStatusCode.OK
            val etag = checkNotNull(initial.headers[HttpHeaders.ETag])
            val lastModified = checkNotNull(initial.headers[HttpHeaders.LastModified])
            modifiedAt.set(Instant.parse("2026-10-24T14:30:00.000000002Z"))
            val changed =
                client.get("/content") {
                    header(HttpHeaders.IfNoneMatch, etag)
                    header(HttpHeaders.IfModifiedSince, lastModified)
                }
            changed.status shouldBe HttpStatusCode.OK
            changed.headers[HttpHeaders.ETag] shouldNotBe etag
            changed.headers[HttpHeaders.LastModified] shouldBe lastModified
            val unchanged =
                client.get("/content") {
                    header(HttpHeaders.IfNoneMatch, checkNotNull(changed.headers[HttpHeaders.ETag]))
                    header(HttpHeaders.IfModifiedSince, "Sat, 24 Oct 2020 14:30:00 GMT")
                }
            unchanged.status shouldBe HttpStatusCode.NotModified
        }
}
