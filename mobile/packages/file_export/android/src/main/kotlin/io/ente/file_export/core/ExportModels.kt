package io.ente.file_export.core

import java.io.File

sealed interface ExportSource {
    data class Bytes(val value: ByteArray) : ExportSource
    data class Path(val value: File) : ExportSource
}

data class ExportRequest(
    val fileName: String,
    val mimeType: String,
    val source: ExportSource
) {
    init {
        require(fileName.isValidFileName()) { "Invalid file name" }
        require(MIME_TYPE.matches(mimeType)) { "Invalid MIME type" }
        if (source is ExportSource.Path) {
            require(source.value.isAbsolute) { "Invalid source path" }
        }
    }

    private companion object {
        val MIME_TYPE = Regex("^[A-Za-z0-9!#\\$&^_.+-]+/[A-Za-z0-9!#\\$&^_.+-]+$")
    }
}

sealed interface ExportResult {
    data class Exported(val location: String) : ExportResult
    data object Cancelled : ExportResult
    data class Failed(
        val reason: ExportFailure,
        val message: String? = null
    ) : ExportResult
}

enum class ExportFailure {
    BUSY,
    SOURCE_MISSING,
    SOURCE_UNREADABLE,
    PRESENTATION_FAILED,
    WRITE_FAILED
}

private fun String.isValidFileName(): Boolean =
    isNotBlank() && this != "." && this != ".." && none {
        it.code < 32 || it in "\\/:*?\"<>|"
    }
