package io.konifer.domain.context

open class InvalidPathException(
    msg: String,
    e: Throwable? = null,
) : RuntimeException(msg, e)

class InvalidQuerySelectorsException(
    msg: String,
    e: Throwable? = null,
) : InvalidPathException(msg, e)

class InvalidDeleteSelectorsException(
    msg: String,
    e: Throwable? = null,
) : InvalidPathException(msg, e)

class ContentTypeNotPermittedException(
    msg: String,
    e: Throwable? = null,
) : RuntimeException(msg, e)

class IllegalRequestedTransformationException(
    msg: String,
) : RuntimeException(msg)
