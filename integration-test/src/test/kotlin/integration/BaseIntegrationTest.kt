package integration

import integration.GarageContainer.Companion.ADMIN_PORT
import integration.GarageContainer.Companion.S3_PORT
import io.konifer.client.KoniferClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Dns
import org.apache.tika.Tika
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path

abstract class BaseIntegrationTest {
    companion object {
        private val modelRootPath: Path = resolveModelRootPath()
        private val modelPackPath: Path = modelRootPath.resolve(SIGLIP2_MODEL_DIR)
        private val koniferImage: DockerImageName = DockerImageName.parse("ghcr.io/dmaiken/konifer:latest")

        val network: Network = Network.newNetwork()

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withDatabaseName("konifer")
                .withUsername("konifer_user")
                .withPassword("konifer_password")
                .withInitScript("init.sql")

        @Container
        @JvmStatic
        val garage: GarageContainer =
            GarageContainer(
                buckets = listOf("assets"),
            ).withNetworkAliases("garage")
                .withExposedPorts(S3_PORT, ADMIN_PORT)
                .withNetwork(network)
                .apply {
                    // Presigned URLs contain garage:3900. The host test client resolves
                    // garage to the Docker host, so the S3 port must remain unchanged.
                    setPortBindings(listOf("$S3_PORT:$S3_PORT"))
                }.withLogConsumer(::logContainerFrame)

        // Copy models instead of bind-mounting because CI runs Gradle inside a container
        // whose paths are not necessarily visible to the Docker host.
        @Container
        @JvmStatic
        val konifer: GenericContainer<*> =
            GenericContainer(
                koniferImage,
            ).withNetwork(network)
                .withExposedPorts(8080)
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource("konifer.conf"),
                    "/app/config/konifer.conf",
                ).withCopyFileToContainer(
                    MountableFile.forHostPath(modelPackPath),
                    "/app/models/$SIGLIP2_MODEL_DIR",
                ).withEnv("PG_PASSWORD", "konifer_password")
                .withEnv("S3_SECRET_KEY", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .dependsOn(postgres, garage)
                .withLogConsumer(::logContainerFrame)
                .waitingFor(
                    Wait
                        .forHttp("/health/live")
                        .forStatusCode(200),
                )

        private val environmentStartup by
            lazy {
                postgres.startOrDumpLogs("postgres")
                garage.startOrDumpLogs("garage")

                konifer.startOrDumpLogs("konifer")
                verifyModelsVisibleToKonifer()
            }

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            environmentStartup
        }

        private fun GenericContainer<*>.startOrDumpLogs(name: String) {
            try {
                start()
            } catch (e: Exception) {
                System.err.println()
                System.err.println("===== $name container logs =====")
                runCatching { logs }
                    .onSuccess { System.err.print(it.ifBlank { "<no logs>\n" }) }
                    .onFailure { System.err.println("<could not read logs: ${it.message}>") }
                System.err.println("===== end $name container logs =====")
                System.err.println()
                throw e
            }
        }

        private fun verifyModelsVisibleToKonifer() {
            val result =
                konifer.execInContainer(
                    "/bin/sh",
                    "-c",
                    """
                    set -eu
                    ls -lh /app/models/$SIGLIP2_MODEL_DIR
                    test -r /app/models/$SIGLIP2_MODEL_DIR/vision_model.onnx
                    test -r /app/models/$SIGLIP2_MODEL_DIR/text_model.onnx
                    test -r /app/models/$SIGLIP2_MODEL_DIR/tokenizer.json
                    """.trimIndent(),
                )

            check(result.exitCode == 0) {
                "Konifer cannot read the SigLIP2 model pack: ${result.stderr}"
            }
            System.err.print(result.stdout)
        }

        private fun logContainerFrame(frame: OutputFrame) {
            System.err.print(frame.utf8String)
        }

        private fun resolveModelRootPath(): Path {
            val requiredFiles =
                listOf(
                    "$SIGLIP2_MODEL_DIR/vision_model.onnx",
                    "$SIGLIP2_MODEL_DIR/text_model.onnx",
                    "$SIGLIP2_MODEL_DIR/tokenizer.json",
                )
            val candidates =
                listOf(
                    Path.of("models"),
                    Path.of("..", "models"),
                ).map {
                    it
                        .toAbsolutePath()
                        .normalize()
                }

            return candidates
                .firstOrNull { candidate ->
                    requiredFiles.all { Files.isRegularFile(candidate.resolve(it)) }
                }?.also { candidate ->
                    // Write to STD error so it shows up in CI
                    System.err.println("Copying SigLIP2 models from $candidate into the Konifer container")
                } ?: error(
                "Could not find SigLIP2 model files. Checked: ${
                    candidates.joinToString()
                }. Current working directory: ${Path.of("").toAbsolutePath().normalize()}",
            )
        }

        private const val SIGLIP2_MODEL_DIR = "siglip2-base-patch16-224"
    }

    protected val client =
        runBlocking {
            KoniferClient.build("http://${konifer.host}:${konifer.getMappedPort(8080)}")
        }

    protected val httpClient =
        HttpClient(OkHttp) {
            engine {
                // Needed for redirects
                dns =
                    Dns { hostname ->
                        if (hostname == "garage") {
                            InetAddress
                                .getAllByName(garage.host)
                                .toList()
                        } else {
                            Dns.SYSTEM.lookup(hostname)
                        }
                    }
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
            defaultRequest {
                url("http://${konifer.host}:${konifer.getMappedPort(8080)}")
            }
        }

    protected val tika = Tika()
}
