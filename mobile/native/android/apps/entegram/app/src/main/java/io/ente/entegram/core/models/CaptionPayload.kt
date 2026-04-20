package io.ente.entegram.core.models

data class CaptionPayload(
    val text: String,
    val images: List<Entry>,
) {
    data class Entry(
        val position: Int,
        val blurHash: String,
        val aspect: AspectPreset,
    )

    companion object {
        val empty = CaptionPayload(text = "", images = emptyList())
    }
}
