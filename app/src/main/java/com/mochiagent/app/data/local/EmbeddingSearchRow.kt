package com.mochiagent.app.data.local

/** Minimal keyset-page projection for semantic scoring. */
data class EmbeddingSearchRow(
    val id: Long,
    val messageId: String,
    val embedding: ByteArray,
    val dimension: Int,
)
