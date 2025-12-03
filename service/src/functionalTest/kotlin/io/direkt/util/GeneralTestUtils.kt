package io.direkt.util

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder

fun ApplicationTestBuilder.createJsonClient(followRedirects: Boolean = true): HttpClient =
    createClient {
        this.followRedirects = followRedirects
        install(ContentNegotiation) {
            json()
        }
    }
