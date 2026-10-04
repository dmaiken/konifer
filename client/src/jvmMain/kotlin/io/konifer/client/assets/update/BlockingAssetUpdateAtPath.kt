package io.konifer.client.assets.update

import io.konifer.client.KoniferResult
import io.konifer.common.http.AssetResponse
import kotlinx.coroutines.runBlocking

/** Blocking counterpart to [AssetUpdateAtPath], with the same immutable metadata operations. */
class BlockingAssetUpdateAtPath internal constructor(
    private val selection: AssetUpdateAtPath,
) {
    /** Returns an independent update with [alt] as the image's alternative text. */
    fun withAlt(alt: String): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withAlt(alt))

    /** Returns an independent update that clears the image's alternative text. */
    fun clearAlt(): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.clearAlt())

    /** Adds or replaces the label identified by [key]. */
    fun withLabel(
        key: String,
        value: String,
    ): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withLabel(key, value))

    /** Adds [labels], replacing the values of existing keys. */
    fun withLabels(labels: Map<String, String>): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withLabels(labels))

    /** Removes the label identified by [key]. */
    fun withoutLabel(key: String): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withoutLabel(key))

    /** Replaces all labels with a snapshot of [labels]. An empty map clears them. */
    fun replaceLabels(labels: Map<String, String>): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.replaceLabels(labels))

    /** Adds [tag] to the existing tags. */
    fun withTag(tag: String): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withTag(tag))

    /** Adds [tags] to the existing tags. */
    fun withTags(tags: Set<String>): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withTags(tags))

    /** Removes [tag] from the existing tags. */
    fun withoutTag(tag: String): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.withoutTag(tag))

    /** Replaces all tags with a snapshot of [tags]. An empty set clears them. */
    fun replaceTags(tags: Set<String>): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.replaceTags(tags))

    /** Sends the complete editable metadata snapshot for the original entry. */
    fun update(): KoniferResult<AssetResponse> = runBlocking { selection.update() }
}
