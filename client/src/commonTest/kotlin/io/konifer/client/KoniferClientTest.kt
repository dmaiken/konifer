package io.konifer.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.coroutines.cancellation.CancellationException

class KoniferClientTest :
    FunSpec({

        test("Closing client closes http client") {
            val engine = MockEngine { respondOk() }
            val client = KoniferClient(HttpClient(engine))

            client.close()

            shouldThrow<CancellationException> {
                client.getAssetLink(
                    path = "/users/123",
                )
            }
        }

        test("Closing client closes http redirect client") {
            val engine = MockEngine { respondOk() }
            val client = KoniferClient(HttpClient(engine))

            client.close()

            shouldThrow<CancellationException> {
                client.getAssetRedirectLocation(
                    path = "/users/123",
                )
            }
        }
    })
