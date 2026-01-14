package io.direkt.infrastructure.http.cache

import io.direkt.infrastructure.http.CustomAttributes.entryIdKey
import io.direkt.infrastructure.http.CustomAttributes.lastModifiedKey
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.LastModifiedVersion
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.util.date.GMTDate
import org.apache.commons.codec.digest.MurmurHash3
import java.time.ZoneOffset
import kotlin.getValue

fun Application.configureConditionalHeaders() {
    install(ConditionalHeaders) {
        version { call, _ ->
            listOfNotNull(
                call.attributes.getOrNull(entryIdKey)?.let { entryId ->
                    val lastModifiedEpoch = call.attributes[lastModifiedKey].toEpochSecond(ZoneOffset.UTC)
                    val rawContent = "$entryId|$lastModifiedEpoch".toByteArray()
                    EntityTagVersion(MurmurHash3.hash32x86(rawContent).toString(16))
                },
                call.attributes.getOrNull(lastModifiedKey)?.let {
                    LastModifiedVersion(
                        GMTDate(it.toEpochSecond(ZoneOffset.UTC) * 1000),
                    )
                },
            )
        }
    }
}
