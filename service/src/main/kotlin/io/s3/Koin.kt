package io.s3

import aws.sdk.kotlin.services.s3.S3Client
import io.asset.store.ObjectStore
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.s3Client
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.s3Module(): Module =
    module {
        val s3ClientProperties =
            S3ClientProperties(
                endpointUrl = environment.config.tryGetString("object-store.s3.endpoint-url"),
                accessKey = environment.config.tryGetString("object-store.s3.access-key"),
                secretKey = environment.config.tryGetString("object-store.s3.secret-key"),
                region =
                    environment.config.tryGetString("object-store.s3.region")
                        ?: throw IllegalStateException("Region must be specified"),
            )
        single<S3Client> {
            s3Client(s3ClientProperties)
        }
        single<ObjectStore> {
            S3Service(get(), s3ClientProperties)
        }
    }
