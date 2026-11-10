package io.konifer.domain.asset

@JvmInline
value class EntryId(
    val id: Long,
) {
    init {
        require(id >= 0) { "Entry Id must be greater than or equal to 0" }
    }

    override fun toString(): String = id.toString()
}

fun Long.toEntryId(): EntryId = EntryId(this)
