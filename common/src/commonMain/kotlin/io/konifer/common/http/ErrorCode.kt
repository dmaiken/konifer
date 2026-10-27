package io.konifer.common.http

import kotlinx.serialization.Serializable

/** Stable categories for API failures. Messages may change independently of these values. */
@Serializable
enum class ErrorCode {
    INVALID_REQUEST,
    INVALID_PATH,
    INVALID_TRANSFORMATION,
    TRANSFORMATION_NOT_ALLOWED,
    ASSET_REJECTED,
    ASSET_TOO_LARGE,
    INVALID_SOURCE,
    SOURCE_UNAVAILABLE,
    FORBIDDEN,
    NOT_FOUND,
    UNSUPPORTED_MEDIA_TYPE,
    INTERNAL_ERROR,

    /** Client fallback for responses with missing or unrecognized codes. The server does not emit this value. */
    UNKNOWN,
}
