package integration

import io.konifer.client.KoniferClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

abstract class BaseIntegrationTest {
    companion object {
        private val koniferStartupTimeout: Duration = Duration.ofMinutes(5)
        private val modelMountPath: Path = resolveModelMountPath()
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
        val minio: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("minio/minio:latest"))
                .withNetwork(network)
                .withNetworkAliases("minio")
                .withCommand("server /data")
                .withEnv("MINIO_ROOT_USER", "minio_admin")
                .withEnv("MINIO_ROOT_PASSWORD", "minio_secret_key")
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/live").forStatusCode(200))

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
                ).withFileSystemBind(
                    modelMountPath.toString(),
                    "/app/models",
                    BindMode.READ_ONLY,
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
                .withFileSystemBind(
                    modelMountPath.toString(),
                    "/app/models",
                    BindMode.READ_ONLY,
                ).withCreateContainerCmdModifier { cmd ->
                    cmd.withEntrypoint(
                        "/bin/sh",
                        "-c",
                        """
                        set -eu
                        echo "Verifying SigLIP2 model mount inside Docker"
                        pwd
                        ls -lh /app/models/siglip2-base-patch16-224
                        test -r /app/models/siglip2-base-patch16-224/vision_model.onnx
                        test -r /app/models/siglip2-base-patch16-224/text_model.onnx
                        test -r /app/models/siglip2-base-patch16-224/tokenizer.json
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

        private fun resolveModelMountPath(): Path {
            val requiredFiles =
                listOf(
                    "siglip2-base-patch16-224/vision_model.onnx",
                    "siglip2-base-patch16-224/text_model.onnx",
                    "siglip2-base-patch16-224/tokenizer.json",
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
                    System.err.println("Mounting SigLIP2 models from $candidate")
                } ?: error(
                "Could not find SigLIP2 model files. Checked: ${
                    candidates.joinToString()
                }. Current working directory: ${Path.of("").toAbsolutePath().normalize()}",
            )
        }
    }

    protected val client =
        runBlocking {
            KoniferClient.build("http://${konifer.host}:${konifer.getMappedPort(8080)}")
        }
}
