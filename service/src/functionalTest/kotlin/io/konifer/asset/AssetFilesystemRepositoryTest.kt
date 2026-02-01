package io.konifer.asset

import io.konifer.config.testInMemory
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.util.createJsonClient
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class AssetFilesystemRepositoryTest {
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
            paths = [
              {
                path = "/**"
                image {
                  lqip = [ "thumbhash", "blurhash" ]
                }
                object-store {
                  bucket = correct-bucket
                }
              }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile").second

            client.get("/assets/profile/-/redirect").apply {
                status shouldBe HttpStatusCode.OK
                headers[HttpHeaders.Location] shouldBe null
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
