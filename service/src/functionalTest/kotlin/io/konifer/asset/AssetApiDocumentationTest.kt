package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.common.http.AssetContentHeaders
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.ManipulationParameters
import io.konifer.testInMemory
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class AssetApiDocumentationTest : BaseFunctionalTest() {
    @Test
    fun `asset operations generate a complete specification including pixel output and multipart uploads`() =
        testInMemory {
            val response = withTimeout(30_000) { client.get("/api/docs/openapi.json") }
            response.status shouldBe HttpStatusCode.OK
            val document = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val paths = document.getValue("paths").jsonObject
            val assets = paths.getValue("/assets/{**}").jsonObject
            assets.keys shouldBe setOf("get", "post", "put", "delete")
            val operations =
                assets.values.map { it.jsonObject } +
                    paths
                        .getValue("/assets")
                        .jsonObject
                        .getValue("post")
                        .jsonObject
            operations.map { it.getValue("operationId").jsonPrimitive.content }.toSet() shouldHaveSize 5
            operations.forEach { operation ->
                operation
                    .getValue("summary")
                    .jsonPrimitive.content
                    .shouldNotBeBlank()
                operation.getValue("tags").jsonArray.map { it.jsonPrimitive.content } shouldBe listOf("Assets")
                operation.getValue("responses").jsonObject.values.forEach {
                    it.jsonObject
                        .getValue("description")
                        .jsonPrimitive.content
                        .shouldNotBeBlank()
                }
            }
            assets.values.forEach { operation ->
                val pathParameter = operation.jsonObject.parameters().single { it["in"]?.jsonPrimitive?.content == "path" }
                pathParameter.getValue("name").jsonPrimitive.content shouldBe "**"
                pathParameter.getValue("required").jsonPrimitive.content shouldBe "true"
            }
            assertFetchDocumentation(assets.getValue("get").jsonObject)
            assertStoreDocumentation(assets.getValue("post").jsonObject)
            assertStoreDocumentation(
                paths
                    .getValue("/assets")
                    .jsonObject
                    .getValue("post")
                    .jsonObject,
            )
            val updateContent =
                assets
                    .getValue("put")
                    .jsonObject
                    .getValue("requestBody")
                    .jsonObject
                    .getValue("content")
                    .jsonObject
            updateContent.keys shouldBe setOf("application/json")
            val updateSchema =
                document
                    .getValue("components")
                    .jsonObject
                    .getValue("schemas")
                    .jsonObject
                    .getValue("AssetMetadata")
                    .jsonObject
            updateSchema.getValue("properties").jsonObject.keys shouldBe setOf("alt", "labels", "tags")
            val deleteResponses =
                assets
                    .getValue("delete")
                    .jsonObject
                    .getValue("responses")
                    .jsonObject
            val deleteLimit =
                assets
                    .getValue("delete")
                    .jsonObject
                    .parameters()
                    .single { it["name"]?.jsonPrimitive?.content == "limit" }
            deleteLimit
                .getValue("schema")
                .jsonObject
                .getValue("enum")
                .jsonArray
                .map { it.jsonPrimitive.content } shouldBe listOf("-1")
            deleteResponses shouldContainKey "204"
            deleteResponses.getValue("204").jsonObject.containsKey("content") shouldBe false
            assertTimestampSchemas(document)
            assertReferencesResolve(document, document)
        }

    private fun assertTimestampSchemas(document: JsonObject) {
        val schemas =
            document
                .getValue("components")
                .jsonObject
                .getValue("schemas")
                .jsonObject
        for ((model, fields) in mapOf("AssetResponse" to listOf("createdAt", "modifiedAt"), "AssetLinkResponse" to listOf("expiresAt"))) {
            val properties =
                schemas
                    .getValue(model)
                    .jsonObject
                    .getValue("properties")
                    .jsonObject
            for (field in fields) {
                val schema = resolveSchema(properties.getValue(field).jsonObject, schemas)
                val type = schema.getValue("type")
                if (type is JsonArray) {
                    type.map { it.jsonPrimitive.content } shouldContainAll listOf("string")
                } else {
                    type.jsonPrimitive.content shouldBe "string"
                }
                schema.getValue("format").jsonPrimitive.content shouldBe "date-time"
            }
        }
    }

    private fun resolveSchema(
        schema: JsonObject,
        schemas: JsonObject,
    ): JsonObject {
        schema["\$ref"]?.jsonPrimitive?.content?.let { reference ->
            return resolveSchema(schemas.getValue(reference.substringAfterLast('/')).jsonObject, schemas)
        }
        (schema["anyOf"] ?: schema["oneOf"])?.jsonArray?.let { options ->
            return resolveSchema(options.first { it.jsonObject["type"]?.toString() != "\"null\"" }.jsonObject, schemas)
        }
        return schema
    }

    private fun assertFetchDocumentation(operation: JsonObject) {
        val parameters = operation.parameters().associateBy { it.getValue("name").jsonPrimitive.content }
        parameters.keys shouldContainAll ManipulationParameters.ALL_TRANSFORMATION_PARAMETERS
        parameters.keys shouldContainAll setOf("profile", "limit", "labels", "s", "Accept", "If-None-Match", "If-Modified-Since")
        val limits =
            parameters
                .getValue("limit")
                .getValue("schema")
                .jsonObject
                .getValue("oneOf")
                .jsonArray
        limits[0]
            .jsonObject
            .getValue("minimum")
            .jsonPrimitive.content
            .toDouble() shouldBe 1.0
        limits[0]
            .jsonObject
            .getValue("maximum")
            .jsonPrimitive.content
            .toDouble() shouldBe Int.MAX_VALUE.toDouble()
        limits[1]
            .jsonObject
            .getValue("enum")
            .jsonArray
            .map { it.jsonPrimitive.content } shouldBe listOf("-1")
        val formats =
            parameters
                .getValue("format")
                .getValue("schema")
                .jsonObject
                .getValue("enum")
                .jsonArray
        formats.map { it.jsonPrimitive.content }.toSet() shouldBe ImageFormat.entries.map { it.format }.toSet()
        val layouts =
            parameters
                .getValue("pixel-layout")
                .getValue("schema")
                .jsonObject
                .getValue("enum")
                .jsonArray
        layouts.map { it.jsonPrimitive.content }.toSet() shouldBe setOf("interleaved", "planar")
        val responses = operation.getValue("responses").jsonObject
        responses.keys shouldContainAll setOf("200", "307", "304", "400", "403", "404")
        val headers =
            responses
                .getValue("200")
                .jsonObject
                .getValue("headers")
                .jsonObject
        headers.keys shouldContainAll AssetContentHeaders.attributeNames
        headers
            .getValue(AssetContentHeaders.ROTATE)
            .jsonObject
            .getValue("schema")
            .jsonObject
            .getValue("enum")
            .jsonArray
            .map { it.jsonPrimitive.content }
            .toSet() shouldBe setOf("0", "90", "180", "270")
        val content =
            responses
                .getValue("200")
                .jsonObject
                .getValue("content")
                .jsonObject
        content.keys shouldContainAll ImageFormat.entries.map { it.mimeType }
        content
            .getValue("application/octet-stream")
            .jsonObject
            .getValue("schema")
            .jsonObject
            .getValue("format")
            .jsonPrimitive.content shouldBe "binary"
        content
            .getValue("application/json")
            .jsonObject
            .getValue("schema")
            .jsonObject
            .getValue("oneOf")
            .jsonArray shouldHaveSize 3
        responses
            .getValue("307")
            .jsonObject
            .getValue("headers")
            .jsonObject shouldContainKey "Location"
    }

    private fun assertStoreDocumentation(operation: JsonObject) {
        val requestBody = operation.getValue("requestBody").jsonObject
        requestBody.getValue("required").jsonPrimitive.content shouldBe "true"
        val content = requestBody.getValue("content").jsonObject
        content.keys shouldBe setOf("application/json", "multipart/form-data")
        val multipart = content.getValue("multipart/form-data").jsonObject
        val schema = multipart.getValue("schema").jsonObject
        schema
            .getValue("required")
            .jsonArray
            .map { it.jsonPrimitive.content }
            .toSet() shouldBe setOf("metadata", "asset")
        schema
            .getValue("properties")
            .jsonObject
            .getValue("asset")
            .jsonObject
            .getValue("format")
            .jsonPrimitive.content shouldBe "binary"
        multipart
            .getValue("encoding")
            .jsonObject
            .getValue("metadata")
            .jsonObject
            .getValue("contentType")
            .jsonPrimitive.content shouldBe "application/json"
        val responses = operation.getValue("responses").jsonObject
        responses.keys shouldContainAll setOf("201", "400", "403", "413", "415", "422", "502", "504")
        responses
            .getValue("201")
            .jsonObject
            .getValue("headers")
            .jsonObject shouldContainKey "Location"
    }

    private fun JsonObject.parameters() = getValue("parameters").jsonArray.map { it.jsonObject }

    private fun assertReferencesResolve(
        element: JsonElement,
        document: JsonObject,
    ) {
        when (element) {
            is JsonObject -> {
                element["${'$'}ref"]?.jsonPrimitive?.content?.let { reference ->
                    reference.startsWith("#/components/schemas/") shouldBe true
                    val name = reference.removePrefix("#/components/schemas/").replace("~1", "/").replace("~0", "~")
                    document
                        .getValue("components")
                        .jsonObject
                        .getValue("schemas")
                        .jsonObject shouldContainKey name
                }
                element.values.forEach { assertReferencesResolve(it, document) }
            }

            is JsonArray -> {
                element.forEach { assertReferencesResolve(it, document) }
            }

            else -> {
                Unit
            }
        }
    }
}
