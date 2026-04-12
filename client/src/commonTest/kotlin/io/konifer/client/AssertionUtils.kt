package io.konifer.client

import io.kotest.matchers.shouldBe
import io.ktor.http.Parameters

fun assertRequestedTransformation(
    parameters: Parameters,
    requestedTransformation: RequestedTransformation?,
) {
    requestedTransformation?.let { requested ->
        requested.width?.let { parameters["w"] shouldBe it.toString() }
        requested.height?.let { parameters["h"] shouldBe it.toString() }
        requested.fit?.let { parameters["fit"] shouldBe it.queryParameterValue }
        requested.gravity?.let { parameters["g"] shouldBe it.queryParameterValue }
        requested.format?.let { parameters["format"] shouldBe it.queryParameterValue }
        requested.rotate?.let { parameters["r"] shouldBe it.queryParameterValue }
        requested.flip?.let { parameters["f"] shouldBe it.queryParameterValue }
        requested.filter?.let { parameters["filter"] shouldBe it.queryParameterValue }
        requested.blur?.let { parameters["blur"] shouldBe it.toString() }
        requested.quality?.let { parameters["q"] shouldBe it.toString() }
        requested.pad?.let { parameters["pad"] shouldBe it.toString() }
        requested.padColor?.let { parameters["pad-c"] shouldBe it }
        requested.profile?.let { parameters["profile"] shouldBe it }
    }
}
