package io.konifer.infrastructure.asset

import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.HttpSource
import io.konifer.common.http.S3Source
import io.konifer.domain.ports.ExternalContentReference
import io.konifer.domain.ports.InvalidAssetSourceException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

class ExternalSourceResolverTest {
    @Test
    fun `resolves and normalizes an HTTP URL`() {
        val reference =
            ExternalSourceResolver.resolve(
                source =
                    AssetSourceRequest(
                        http = HttpSource(url = "https://assets.example/images/../asset.png"),
                    ),
                deprecatedUrl = null,
            )

        reference shouldBe ExternalContentReference.Url(URI.create("https://assets.example/asset.png"))
    }

    @Test
    fun `uses the deprecated URL when the HTTP source URL is absent`() {
        val reference =
            ExternalSourceResolver.resolve(
                source = AssetSourceRequest(),
                deprecatedUrl = "https://assets.example/asset.png",
            )

        reference shouldBe ExternalContentReference.Url(URI.create("https://assets.example/asset.png"))
    }

    @Test
    fun `the HTTP source URL takes precedence over the deprecated URL`() {
        val reference =
            ExternalSourceResolver.resolve(
                source =
                    AssetSourceRequest(
                        http = HttpSource(url = "https://assets.example/current.png"),
                    ),
                deprecatedUrl = "https://assets.example/deprecated.png",
            )

        reference shouldBe ExternalContentReference.Url(URI.create("https://assets.example/current.png"))
    }

    @Test
    fun `resolves an S3 object ARN with a nested key`() {
        val reference =
            ExternalSourceResolver.resolve(
                source =
                    AssetSourceRequest(
                        s3 = S3Source(arn = "arn:aws:s3:::asset-sources/images/2026/asset.png"),
                    ),
                deprecatedUrl = null,
            )

        reference shouldBe
            ExternalContentReference.S3Object(
                bucket = "asset-sources",
                key = "images/2026/asset.png",
            )
    }

    @Test
    fun `rejects a request without an external source`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                ExternalSourceResolver.resolve(
                    source = AssetSourceRequest(),
                    deprecatedUrl = null,
                )
            }

        exception.message shouldBe "URL or S3 ARN must be supplied"
    }

    @Test
    fun `rejects a request containing both a URL and an ARN`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                ExternalSourceResolver.resolve(
                    source =
                        AssetSourceRequest(
                            http = HttpSource(url = "https://assets.example/asset.png"),
                            s3 = S3Source(arn = "arn:aws:s3:::asset-sources/asset.png"),
                        ),
                    deprecatedUrl = null,
                )
            }

        exception.message shouldBe "Only one of source.http.url or source.s3.arn, or url can be supplied"
    }

    @Test
    fun `rejects a malformed URL`() {
        val exception =
            shouldThrow<InvalidAssetSourceException> {
                ExternalSourceResolver.resolve(
                    source = AssetSourceRequest(http = HttpSource(url = "https://[invalid")),
                    deprecatedUrl = null,
                )
            }

        exception.message shouldBe "https://[invalid is not a valid URL"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "not-an-arn",
            "arn:aws:s3:::asset-sources/",
        ],
    )
    fun `rejects a malformed ARN`(arn: String) {
        val exception =
            shouldThrow<InvalidAssetSourceException> {
                ExternalSourceResolver.resolve(
                    source = AssetSourceRequest(s3 = S3Source(arn = arn)),
                    deprecatedUrl = null,
                )
            }

        exception.message shouldBe "Invalid ARN"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "arn:aws:lambda:us-east-1:123456789012:function:asset-reader",
            "arn:aws:s3:us-east-1::asset-sources/asset.png",
            "arn:aws:s3::123456789012:asset-sources/asset.png",
        ],
    )
    fun `rejects ARNs that are not conventional S3 object ARNs`(arn: String) {
        val exception =
            shouldThrow<InvalidAssetSourceException> {
                ExternalSourceResolver.resolve(
                    source = AssetSourceRequest(s3 = S3Source(arn = arn)),
                    deprecatedUrl = null,
                )
            }

        exception.message shouldBe "Not an S3 object ARN"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "arn:aws:s3:::asset-sources",
            "arn:aws:s3:::/asset.png",
        ],
    )
    fun `rejects an S3 ARN without both a bucket and key`(arn: String) {
        val exception =
            shouldThrow<InvalidAssetSourceException> {
                ExternalSourceResolver.resolve(
                    source = AssetSourceRequest(s3 = S3Source(arn = arn)),
                    deprecatedUrl = null,
                )
            }

        exception.message shouldBe "S3 object ARN must contain a bucket and key"
    }
}
