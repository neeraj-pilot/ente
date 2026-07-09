package io.ente.ensu.llm



data class LlmModelTarget(
    val id: String,
    val url: String,
    val mmprojUrl: String? = null,
    val contextLength: Int? = null,
    val maxTokens: Int? = null
)

data class DownloadProgress(
    val percent: Int?,
    val status: String,
    val failure: DownloadFailure? = null,
    val phase: DownloadPhase = DownloadPhase.Downloading
)

enum class DownloadPhase {
    Downloading,
    Loading,
    Ready,
    Failed
}

sealed class DownloadFailure(override val message: String) : Exception(message) {
    class Http(val status: Int) : DownloadFailure("Download failed: HTTP $status")
    class InvalidContent(message: String) : DownloadFailure(message)
    class InsufficientSpace : DownloadFailure(
        "Not enough storage space to download the model. Please free up space and try again."
    )
    class TimedOut : DownloadFailure("Download timed out")
    class Failed(message: String) : DownloadFailure(message)
}

enum class LlmMessageRole {
    User,
    Assistant,
    System
}

data class LlmMessage(
    val text: String,
    val role: LlmMessageRole,
    val hasAttachments: Boolean = false
)

data class GenerationSummary(
    val jobId: Long,
    val generatedTokens: Int,
    val totalTimeMs: Long?
)
