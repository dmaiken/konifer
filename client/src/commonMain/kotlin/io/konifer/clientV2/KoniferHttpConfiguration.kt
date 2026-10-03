package io.konifer.clientV2

import kotlin.jvm.JvmField
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * HTTP timeouts shared by all requests, including redirected content downloads.
 *
 * Unset values preserve the engine's defaults; there is no overall request deadline by default.
 * Use [Duration.INFINITE] to explicitly disable a timeout. Finite values must be at least one
 * millisecond and are truncated to whole milliseconds. Connection and socket timeout support
 * depends on the selected Ktor engine.
 *
 * Kotlin callers can use named constructor arguments with [Duration] values. Java callers can
 * use [Builder] with millisecond values.
 */
class KoniferHttpConfiguration(
    /** Time allowed for the HTTP call, from sending the request to receiving the response. */
    val requestTimeout: Duration? = null,
    /** Time allowed to establish a connection to the server. */
    val connectTimeout: Duration? = null,
    /** Maximum inactivity between packets while reading or writing content. */
    val socketTimeout: Duration? = null,
) {
    init {
        validateTimeout("requestTimeout", requestTimeout)
        validateTimeout("connectTimeout", connectTimeout)
        validateTimeout("socketTimeout", socketTimeout)
    }

    companion object {
        /** Configuration that preserves the selected HTTP engine's timeout defaults. */
        @JvmField
        val Default: KoniferHttpConfiguration = KoniferHttpConfiguration()

        private fun validateTimeout(
            name: String,
            timeout: Duration?,
        ) {
            require(timeout == null || timeout >= 1.milliseconds) {
                "$name must be at least one millisecond or Duration.INFINITE"
            }
        }
    }

    /** Java-friendly builder. `null` retains engine defaults; [Long.MAX_VALUE] disables a timeout. */
    class Builder {
        private var requestTimeout: Duration? = null
        private var connectTimeout: Duration? = null
        private var socketTimeout: Duration? = null

        /** Sets the request timeout in milliseconds, or retains the engine default when `null`. */
        fun requestTimeoutMillis(timeoutMillis: Long?): Builder = apply { requestTimeout = timeoutMillis?.milliseconds }

        /** Sets the connection timeout in milliseconds, or retains the engine default when `null`. */
        fun connectTimeoutMillis(timeoutMillis: Long?): Builder = apply { connectTimeout = timeoutMillis?.milliseconds }

        /** Sets the socket inactivity timeout in milliseconds, or retains the engine default when `null`. */
        fun socketTimeoutMillis(timeoutMillis: Long?): Builder = apply { socketTimeout = timeoutMillis?.milliseconds }

        /** Creates an immutable timeout configuration from the current builder values. */
        fun build(): KoniferHttpConfiguration =
            KoniferHttpConfiguration(
                requestTimeout = requestTimeout,
                connectTimeout = connectTimeout,
                socketTimeout = socketTimeout,
            )
    }
}
