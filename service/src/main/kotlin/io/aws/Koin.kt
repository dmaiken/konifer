package io.aws

import aws.sdk.kotlin.services.s3.S3Client
import io.LocalstackProperties
import io.asset.store.ObjectStore
import io.ktor.server.application.Application
import io.s3Client
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.awsModule(): Module =
    module {
        val useMock = environment.config.propertyOrNull("aws.mock")?.getString().toBoolean()
        val region = environment.config.propertyOrNull("localstack.region")?.getString() ?: "us-east-1" // TODO
        single<S3Client> {
            if (useMock) {
                s3Client(
                    LocalstackProperties(
                        region = region,
                        accessKey = environment.config.property("localstack.accessKey").getString(),
                        secretKey = environment.config.property("localstack.secretKey").getString(),
                        endpointUrl = environment.config.property("localstack.endpointUrl").getString(),
                    ),
                )
            } else {
                s3Client()
            }
        }
        single<ObjectStore> {
            val port = environment.config.property("localstack.port").getString().toInt()
            val awsProperties =
                AWSProperties(
                    host = if (useMock) "localhost.localstack.cloud:$port" else "s3-$region.amazonaws.com",
                    region = region,
                )
            S3Service(get(), awsProperties)
        }
    }
