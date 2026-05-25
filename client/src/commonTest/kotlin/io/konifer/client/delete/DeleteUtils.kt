package io.konifer.client.delete

import io.konifer.client.harness.assertLabels
import io.konifer.client.harness.assertLimit
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngine.Companion.invoke
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

fun configureMockEngineHappy(
    expectedPath: String,
    statusCode: HttpStatusCode = HttpStatusCode.NoContent,
    labels: Map<String, String> = emptyMap(),
    limit: Int = 1,
): MockEngine =
    MockEngine { request ->
        request.url.encodedPath shouldBe expectedPath
        request.method shouldBe HttpMethod.Delete
        assertLabels(
            parameters = request.url.parameters,
            labels = labels,
        )
        assertLimit(
            parameters = request.url.parameters,
            expectedLimit = limit,
        )

        respond(
            content = "",
            status = statusCode,
        )
    }
