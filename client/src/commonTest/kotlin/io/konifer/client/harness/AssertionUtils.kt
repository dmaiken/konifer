package io.konifer.client.harness

import io.konifer.client.RequestedTransformation
import io.konifer.common.image.ALL_RESERVED_PARAMETERS
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
        val strip =
            requested.strip
                .joinToString(",") { it.name.lowercase() }
                .takeIf { it.isNotBlank() }
        if (strip != null) {
            parameters["strip"] shouldBe strip
        } else {
            parameters["strip"] shouldBe null
        }
        requested.colorSpace?.let { parameters["cs"] shouldBe it.queryParameterValue }
    }
}

fun assertLabels(
    parameters: Parameters,
    labels: Map<String, String>,
) {
    labels.forEach { (key, value) ->
        val labelKey = key.lowercase()
        val expectedParameterName =
            if (labelKey in ALL_RESERVED_PARAMETERS) {
                "label:$labelKey"
            } else {
                labelKey
            }

        parameters[expectedParameterName] shouldBe value
    }
}

fun assertLimit(
    parameters: Parameters,
    expectedLimit: Int = 1,
) {
    if (expectedLimit == 1) {
        (parameters["limit"] ?: "1") shouldBe "1"
    } else {
        parameters["limit"] shouldBe expectedLimit.toString()
    }
}
