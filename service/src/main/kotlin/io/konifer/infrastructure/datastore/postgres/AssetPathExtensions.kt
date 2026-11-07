package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.asset.AssetPath
import org.jooq.postgres.extensions.types.Ltree

private const val TREE_ROOT = "root"

fun AssetPath.toLtree(): Ltree = Ltree.valueOf(if (this == AssetPath.ROOT) TREE_ROOT else TREE_ROOT + value.replace('/', '.'))

fun Ltree.toAssetPath(): AssetPath {
    val path = data()
    require(path == TREE_ROOT || path.startsWith("$TREE_ROOT.")) { "Invalid asset tree path: $path" }
    return if (path == TREE_ROOT) AssetPath.ROOT else AssetPath(path.removePrefix("$TREE_ROOT.").replace('.', '/'))
}
