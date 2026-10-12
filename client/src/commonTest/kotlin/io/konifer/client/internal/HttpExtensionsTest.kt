package io.konifer.client.internal

import io.konifer.client.assets.fetch.EntryId
import io.konifer.client.assets.fetch.None
import io.konifer.client.assets.fetch.OrderBy
import io.konifer.client.assets.fetch.Recursive
import io.konifer.client.assets.fetch.RequestedTransformation
import io.konifer.client.assets.fetch.requestedTransformation
import io.konifer.client.harness.allTransformationsBuilder
import io.konifer.client.harness.allTransformationsDsl
import io.konifer.client.harness.assertRequestedTransformation
import io.konifer.common.selector.Order
import io.konifer.common.selector.ReturnFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

class HttpExtensionsTest :
    FunSpec({
        test("no selector appends just the return format") {
            val url =
                URLBuilder().apply {
                    appendPathSegments("assets", "users", "123")
                    appendQuerySelectors(ReturnFormat.INFO, None)
                }

            url.build().encodedPath shouldBe "/assets/users/123/-/info"
        }

        test("order and entry selectors append their path segments") {
            val ordered =
                URLBuilder().apply {
                    appendPathSegments("assets", "users", "123")
                    appendQuerySelectors(ReturnFormat.LINK, OrderBy(Order.MODIFIED))
                }
            val entry =
                URLBuilder().apply {
                    appendPathSegments("assets", "users", "123")
                    appendQuerySelectors(ReturnFormat.CONTENT, EntryId(42))
                }

            ordered.build().encodedPath shouldBe "/assets/users/123/-/modified/link"
            entry.build().encodedPath shouldBe "/assets/users/123/-/entry/42/content"
        }

        test("recursive selector works without a return format") {
            val url =
                URLBuilder().apply {
                    appendPathSegments("assets", "users")
                    appendQuerySelectors(null, Recursive)
                }

            url.build().encodedPath shouldBe "/assets/users/-/recursive"
        }

        test("labels use lowercase names and escape reserved parameter names") {
            val url =
                URLBuilder().apply {
                    appendLabels(mapOf("Camera" to "phone", "FORMAT" to "display", "limit" to "important"))
                    appendLimit(2)
                }

            url.parameters["camera"] shouldBe "phone"
            url.parameters["label:format"] shouldBe "display"
            url.parameters["label:limit"] shouldBe "important"
            url.parameters["limit"] shouldBe "2"
        }

        test("all transformation options are encoded as query parameters") {
            listOf(allTransformationsDsl, allTransformationsBuilder).forEach { transformation ->
                val url = URLBuilder().apply { appendTransformationParameters(transformation) }

                assertRequestedTransformation(url.build().parameters, allTransformationsDsl)
            }
        }

        test("pixel channels are encoded when they are the only transformation option") {
            listOf(
                requestedTransformation { pixelChannels = "bGrA" },
                RequestedTransformation.Builder().pixelChannels("bGrA").build(),
            ).forEach { transformation ->
                val url = URLBuilder().apply { appendTransformationParameters(transformation) }

                url.parameters.entries() shouldBe mapOf("pixel-channels" to listOf("bGrA")).entries
            }
        }

        test("original variant leaves transformation query parameters empty") {
            val url = URLBuilder().apply { appendTransformationParameters(RequestedTransformation.OriginalVariant) }

            url.parameters.isEmpty() shouldBe true
        }
    })
