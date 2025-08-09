package io.asset.repository

import org.jooq.postgres.extensions.types.Ltree

object PathAdapter {
    private val validPathRegex = Regex("^(?!/)[a-zA-Z0-9_~!$'()*+=@/-]*$")
    private const val TREE_PATH_DELIMITER = "."
    private const val URI_PATH_DELIMITER = "/"
    const val TREE_ROOT = "root"

    fun toTreePathFromUriPath(uriPath: String): Ltree {
        val trimmedPath =
            uriPath.removePrefix(URI_PATH_DELIMITER)
                .removeSuffix(URI_PATH_DELIMITER)
        if (!trimmedPath.matches(validPathRegex)) {
            throw IllegalArgumentException("Invalid path: $trimmedPath")
        }
        return trimmedPath.replace(URI_PATH_DELIMITER, TREE_PATH_DELIMITER).let {
            if (it.isEmpty()) {
                TREE_ROOT
            } else {
                TREE_ROOT + TREE_PATH_DELIMITER + it
            }.let { path ->
                Ltree.valueOf(path)
            }
        }
    }

    fun toUriPath(treePath: String): String = treePath.removePrefix(TREE_ROOT).replace(TREE_PATH_DELIMITER, URI_PATH_DELIMITER)
}

fun Ltree.toPath(): String = PathAdapter.toUriPath(this.data())
