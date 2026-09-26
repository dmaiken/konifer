package integration

import com.github.dockerjava.api.command.InspectContainerResponse
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName

class GarageContainer(
    image: String = "dxflrs/garage:v2.4.1",
    private val buckets: List<String> = listOf("test-bucket"),
    val accessKey: String = "GK0123456789abcdef0123456789abcdef",
    secretKey: String = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    region: String = "garage",
) : GenericContainer<GarageContainer>(DockerImageName.parse(image)) {
    companion object {
        const val S3_PORT = 3900
        const val ADMIN_PORT = 3903
    }

    init {
        require(buckets.isNotEmpty()) { "At least one bucket must be specified" }

        val garageToml =
            """
            metadata_dir = "/tmp/garage/meta"
            data_dir = "/tmp/garage/data"
            db_engine = "sqlite"
            replication_factor = 1

            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"
            rpc_secret = "c2a4f470129cf9eb5083cfc09192461a29388df634f179116e02613d7890e0c1"

            [s3_api]
            api_bind_addr = "[::]:$S3_PORT"
            s3_region = "$region"
            root_domain = ".s3.garage.localhost"

            [admin]
            api_bind_addr = "[::]:$ADMIN_PORT"
            admin_token = "94f0e6ce7429188d30e386fcbc2713f02128e469550b07357c32d96c968f44d1"
            """.trimIndent()

        withExposedPorts(S3_PORT)
        withCopyToContainer(Transferable.of(garageToml), "/etc/garage.toml")
        withEnv("GARAGE_DEFAULT_ACCESS_KEY", accessKey)
        withEnv("GARAGE_DEFAULT_SECRET_KEY", secretKey)
        withEnv("GARAGE_DEFAULT_BUCKET", buckets.first())
        withCommand("/garage", "server", "--single-node", "--default-bucket")
        waitingFor(Wait.forListeningPorts(S3_PORT))
    }

    override fun containerIsStarted(containerInfo: InspectContainerResponse) {
        // The first bucket is auto-created via GARAGE_DEFAULT_BUCKET.
        // Provision any additional buckets requested for the test suite:
        buckets.drop(1).forEach { bucket ->
            createBucket(bucket)
        }
    }

    fun createBucket(bucketName: String) {
        val createResult = execInContainer("/garage", "bucket", "create", bucketName)
        check(createResult.exitCode == 0) {
            "Failed to create bucket '$bucketName': ${createResult.stderr}"
        }

        val allowResult =
            execInContainer(
                "/garage",
                "bucket",
                "allow",
                "--read",
                "--write",
                "--owner",
                bucketName,
                "--key",
                accessKey,
            )
        check(allowResult.exitCode == 0) {
            "Failed to grant permissions on bucket '$bucketName': ${allowResult.stderr}"
        }
    }
}
