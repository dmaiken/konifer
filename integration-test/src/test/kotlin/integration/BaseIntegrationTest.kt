package integration

import io.konifer.client.KoniferClient
import io.kotest.engine.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.time.Duration

abstract class BaseIntegrationTest {

    companion object {
        val network: Network = Network.newNetwork()

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withNetwork(network)
            .withNetworkAliases("postgres")
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

        @Container
        @JvmStatic
        val createBuckets: GenericContainer<*> = GenericContainer(DockerImageName.parse("minio/mc:latest"))
            .withNetwork(network)
            .dependsOn(minio)
            .withNetworkAliases("createbuckets")
            .withCreateContainerCmdModifier { cmd ->
                cmd.withEntrypoint(
                    "/bin/sh",
                    "-c",
                    "mc alias set myminio http://minio:9000 minio_admin minio_secret_key && mc mb myminio/assets && mc anonymous set public myminio/assets"
                )
            }
            .withStartupCheckStrategy(
                OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(15))
            )
            .withLogConsumer { frame: OutputFrame -> print(frame.utf8String) }

        @Container
        @JvmStatic
        val konifer: GenericContainer<*> = GenericContainer(
            DockerImageName.parse("ghcr.io/dmaiken/konifer:latest")
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
            .withLogConsumer { frame: OutputFrame -> print(frame.utf8String) }
            .waitingFor(Wait.forHttp("/health").forStatusCode(200))

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            postgres.start()

            minio.start()
            createBuckets.start()

            konifer.start()
        }
    }

    protected val client = KoniferClient.build("http://${konifer.host}:${konifer.getMappedPort(8080)}")
}
