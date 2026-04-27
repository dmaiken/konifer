package integration

import io.kotest.engine.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Path
import java.nio.file.Paths

abstract class BaseIntegrationTest {

    companion object {
        val network: Network = Network.newNetwork()

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withNetwork(network)
            .withNetworkAliases("db")
            .withDatabaseName("konifer")
            .withUsername("konifer_user")
            .withPassword("konifer_password")
            .withInitScript("init.sql")

        @Container
        @JvmStatic
        val minio: GenericContainer<*> = GenericContainer(DockerImageName.parse("minio/minio:latest"))
            .withNetwork(network)
            .withNetworkAliases("minio")
            .withCommand("server /data")
            .withEnv("MINIO_ROOT_USER", "minio_admin")
            .withEnv("MINIO_ROOT_PASSWORD", "minio_secret_key")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forStatusCode(200))

        val projectRoot: Path = Paths.get(System.getProperty("user.dir")).let { currentDir ->
            if (currentDir.endsWith("integration-test")) currentDir.parent else currentDir
        }

        @Container
        @JvmStatic
        val konifer: GenericContainer<*> = GenericContainer(
            ImageFromDockerfile("konifer-integration-test", false)
                .withDockerfile(projectRoot.resolve("Dockerfile"))
                .withFileFromPath(".", projectRoot)
        )
            .withNetwork(network)
            .withExposedPorts(8080)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("konifer.conf"),
                "/app/config/konifer.conf"
            )
            .withEnv("PG_PASSWORD", "konifer_password")
            .withEnv("S3_SECRET_KEY", "minio_secret_key")
            .dependsOn(postgres, minio)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200))

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            postgres.start()
            minio.start()
            konifer.start()
        }
    }
}
