package io.direkt.asset.context

open class InvalidPathException(
    msg: String,
    e: Throwable? = null,
) : RuntimeException(msg, e)

class InvalidQueryModifiersException(
    msg: String,
    e: Throwable? = null,
) : InvalidPathException(msg, e)

class InvalidDeleteModifiersException(
    msg: String,
    e: Throwable? = null,
) : InvalidPathException(msg, e)

class ContentTypeNotPermittedException(
    msg: String,
    e: Throwable? = null,
) : RuntimeException(msg, e)
