package io.konifer.config

import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

class LocalstackContainerManager {
    companion object {
        val image: DockerImageName = DockerImageName.parse("localstack/localstack:4.6.0")
    }

    private var started = false
    private val localstack =
        LocalStackContainer(image)
            .withServices(LocalStackContainer.Service.S3)

    init {
        localstack.start()
        started = true
    }

    fun getAccessKey(): String = localstack.accessKey

    fun getSecretKey(): String = localstack.secretKey

    fun getRegion(): String = localstack.region

    fun getEndpointUrl(): String = localstack.endpoint.toURL().toString()

    fun getPort(): Int = localstack.firstMappedPort

    fun stop() = localstack.stop()
}
