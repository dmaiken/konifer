package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory.testImage
import io.konifer.common.http.StoreAssetRequest
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.testInMemory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class AssetFilesystemRepositoryTest : BaseFunctionalTest() {
    val mountPath: Path =
        Paths
            .get(System.getProperty("java.io.tmpdir"))
            .resolve("object-store-test")
            .resolve("mnt")

    @BeforeEach
    fun beforeEach() {
        if (!Files.exists(mountPath.parent)) {
            Files.createDirectories(mountPath.parent)
        }

        if (!Files.exists(mountPath)) {
            Files.createDirectories(mountPath)
        }

        emptyDirectory(mountPath)
    }

    @Test
    fun `asset redirect defaults to content response`() =
        testInMemory(
            """
            object-store {
              provider = filesystem
              filesystem {
                mount-path = ${mountPath.absolutePathString()}
              }
            }
            http {
              public-url = "https://localhost:9000"
            }
            paths {
              "/**" {
                lqip = [ "thumbhash", "blurhash" ]
                object-store {
                  bucket = correct-bucket
                }
              }
            }
            """.trimIndent(),
        ) {
            configureClient { followRedirects = false }
            val (image, attributes) = testImage()
            val asset =
                konifer()
                    .storeAsset(
                        path = "profile",
                        format = attributes.format,
                        request = StoreAssetRequest(),
                        bytes = image,
                    ).shouldBeSuccessful()

            client.get("/assets/profile/-/redirect").apply {
                status shouldBe HttpStatusCode.TemporaryRedirect
                with(headers[HttpHeaders.Location]) {
                    this shouldNotBe null
                    this shouldContain "/-/entry/${asset.body.entryId}/content"
                }
            }
        }

    private fun emptyDirectory(path: Path) {
        Files.walk(path).use { walk ->
            walk
                .sorted(Comparator.reverseOrder())
                .filter { !it.equals(path) } // Skip the root directory itself
                .forEach { Files.delete(it) }
        }
    }
}
