package io.direkt.infrastructure.datastore.postgres.scheduling

import com.github.kagkarlsson.scheduler.serializer.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import java.nio.charset.StandardCharsets

/**
 * From db-scheduler example for Kotlin serializer
 * https://github.com/kagkarlsson/db-scheduler/blob/master/examples/features/src/main/java/com/github/kagkarlsson/examples/kotlin/KotlinSerializer.kt
 */
class KotlinSerializer : Serializer {
    private val charset = StandardCharsets.UTF_8

    override fun serialize(data: Any?): ByteArray {
        if (data == null) {
            return ByteArray(0)
        }
        val serializer = serializer(data.javaClass)
        return Json.encodeToString(serializer, data).toByteArray(charset)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> deserialize(
        clazz: Class<T>,
        serializedData: ByteArray?,
    ): T? {
        if (serializedData == null || clazz == Void::class.java) {
            return null
        }

        // If the class is serialized as Any (i.e. java.lang.Object), decode as generic JSON
        if (clazz == Any::class.java) {
            return Json.decodeFromString(JsonElement.serializer(), serializedData.decodeToString()) as T
        }

        // Hackish workaround?
        // https://github.com/Kotlin/kotlinx.serialization/issues/1134
        // https://stackoverflow.com/questions/64284767/replace-jackson-with-kotlinx-serialization-in-javalin-framework/64285478#64285478

        val deserializer = serializer(clazz) as KSerializer<T>
        return Json.decodeFromString(deserializer, String(serializedData, charset))
    }
}
