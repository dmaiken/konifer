package integration

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
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

abstract class BaseIntegrationTest {
    companion object {
        private val koniferStartupTimeout: Duration = Duration.ofMinutes(5)
        private val modelRootPath: Path = resolveModelRootPath()
        private val modelPackPath: Path = modelRootPath.resolve(SIGLIP2_MODEL_DIR)
        private val koniferImage: DockerImageName = DockerImageName.parse("ghcr.io/dmaiken/konifer:latest")

        val network: Network = Network.newNetwork()

        private const val MINIO_PORT = 9000

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
        val minio: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("minio/minio:latest"))
                .withNetwork(network)
                .withNetworkAliases("minio")
                .withCommand("server /data")
                .withEnv("MINIO_ROOT_USER", "minio_admin")
                .withEnv("MINIO_ROOT_PASSWORD", "minio_secret_key")
                .withExposedPorts(MINIO_PORT)
                .apply {
                    // Needed for redirect testing
                    setPortBindings(listOf("$MINIO_PORT:$MINIO_PORT"))
                }.waitingFor(Wait.forHttp("/minio/health/live").forStatusCode(200))

        @Container
        @JvmStatic
        val createBuckets: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("minio/mc:latest"))
                .withNetwork(network)
                .dependsOn(minio)
                .withNetworkAliases("createbuckets")
                .withCreateContainerCmdModifier { cmd ->
                    cmd.withEntrypoint(
                        "/bin/sh",
                        "-c",
                        "mc alias set myminio http://minio:9000 minio_admin minio_secret_key && mc mb myminio/assets && mc anonymous set public myminio/assets",
                    )
                }.withStartupCheckStrategy(
                    OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(15)),
                ).withLogConsumer(::logContainerFrame)

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
                .withEnv("S3_SECRET_KEY", "minio_secret_key")
                .dependsOn(postgres, minio)
                .withLogConsumer(::logContainerFrame)
                .waitingFor(
                    Wait
                        .forHttp("/health")
                        .forStatusCode(200)
                        .withStartupTimeout(koniferStartupTimeout),
                )

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            postgres.startOrDumpLogs("postgres")

            minio.startOrDumpLogs("minio")
            createBuckets.startOrDumpLogs("createBuckets")

            verifyModelMountVisibleToDocker()
            konifer.startOrDumpLogs("konifer")
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

        private fun verifyModelMountVisibleToDocker() {
            GenericContainer(koniferImage)
                .withCopyFileToContainer(
                    MountableFile.forHostPath(modelPackPath),
                    "/app/models/$SIGLIP2_MODEL_DIR",
                ).withCreateContainerCmdModifier { cmd ->
                    cmd.withEntrypoint(
                        "/bin/sh",
                        "-c",
                        """
                        set -eu
                        echo "Verifying SigLIP2 models inside Docker"
                        pwd
                        ls -lh /app/models/$SIGLIP2_MODEL_DIR
                        test -r /app/models/$SIGLIP2_MODEL_DIR/vision_model.onnx
                        test -r /app/models/$SIGLIP2_MODEL_DIR/text_model.onnx
                        test -r /app/models/$SIGLIP2_MODEL_DIR/tokenizer.json
                        """.trimIndent(),
                    )
                }.withStartupCheckStrategy(
                    OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(30)),
                ).withLogConsumer(::logContainerFrame)
                .startOrDumpLogs("konifer-model-mount")
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
                    System.err.println("Copying SigLIP2 models from $candidate into Docker containers")
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
                        if (hostname == "minio") {
                            InetAddress
                                .getAllByName(minio.host)
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
