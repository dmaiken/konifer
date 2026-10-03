package io.konifer.clientV2.assets.update

import io.konifer.clientV2.KoniferV2Result
import io.konifer.common.http.AssetResponse
import kotlinx.coroutines.runBlocking

/** Blocking counterpart to [AssetUpdateAtPath], with the same immutable metadata operations. */
class BlockingAssetUpdateAtPath internal constructor(
    private val selection: AssetUpdateAtPath,
) {
    fun withAlt(alt: String): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withAlt(alt))

    fun clearAlt(): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.clearAlt())

    fun withLabel(
        key: String,
        value: String,
    ): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withLabel(key, value))

    fun withLabels(labels: Map<String, String>): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withLabels(labels))

    fun withoutLabel(key: String): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withoutLabel(key))

    fun replaceLabels(labels: Map<String, String>): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.replaceLabels(labels))

    fun withTag(tag: String): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withTag(tag))

    fun withTags(tags: Set<String>): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withTags(tags))

    fun withoutTag(tag: String): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withoutTag(tag))

    fun replaceTags(tags: Set<String>): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.replaceTags(tags))

    fun update(): KoniferV2Result<AssetResponse> = runBlocking { selection.update() }
}
