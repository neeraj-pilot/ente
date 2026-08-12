package io.ente.file_export.core

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Exporter(
    private val contentResolver: ContentResolver
) {
    private data class ActiveExport(
        val request: ExportRequest,
        val completion: (ExportResult) -> Unit
    )

    private var active: ActiveExport? = null
    private var isClosed = false
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "file-export").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @MainThread
    fun export(
        request: ExportRequest,
        launch: (Intent) -> Unit,
        completion: (ExportResult) -> Unit
    ) {
        if (isClosed) {
            completion(ExportResult.Failed(ExportFailure.PRESENTATION_FAILED))
            return
        }
        if (active != null) {
            completion(ExportResult.Failed(ExportFailure.BUSY))
            return
        }
        sourceFailure(request.source)?.let {
            completion(ExportResult.Failed(it))
            return
        }

        active = ActiveExport(request, completion)
        try {
            launch(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = request.mimeType
                    putExtra(Intent.EXTRA_TITLE, request.fileName)
                }
            )
        } catch (error: Exception) {
            finish(
                ExportResult.Failed(
                    ExportFailure.PRESENTATION_FAILED,
                    error.message
                )
            )
        }
    }

    @MainThread
    fun selected(uri: Uri) {
        val current = active ?: return
        try {
            executor.execute {
                val result = write(current.request.source, uri)
                mainHandler.post {
                    if (active === current) finish(result)
                }
            }
        } catch (error: Exception) {
            finish(ExportResult.Failed(ExportFailure.WRITE_FAILED, error.message))
        }
    }

    @MainThread
    fun cancel() {
        if (active != null) finish(ExportResult.Cancelled)
    }

    @MainThread
    fun selectionFailed() {
        if (active != null) {
            finish(ExportResult.Failed(ExportFailure.PRESENTATION_FAILED))
        }
    }

    @MainThread
    fun close() {
        if (isClosed) return
        isClosed = true
        active = null
        executor.shutdownNow()
    }

    private fun write(source: ExportSource, destination: Uri): ExportResult {
        var failure = ExportFailure.WRITE_FAILED
        return try {
            when (source) {
                is ExportSource.Bytes -> outputStream(destination).use {
                    it.write(source.value)
                }

                is ExportSource.Path -> {
                    val input = try {
                        FileInputStream(source.value)
                    } catch (error: FileNotFoundException) {
                        failure = sourceFailure(source) ?: ExportFailure.SOURCE_UNREADABLE
                        throw error
                    }
                    input.use {
                        outputStream(destination).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                if (Thread.currentThread().isInterrupted) {
                                    throw InterruptedIOException()
                                }
                                val count = it.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
            ExportResult.Exported(destination.toString())
        } catch (error: Exception) {
            ExportResult.Failed(failure, error.message)
        }
    }

    private fun outputStream(destination: Uri) =
        contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("Unable to open export destination")

    private fun sourceFailure(source: ExportSource): ExportFailure? = when (source) {
        is ExportSource.Bytes -> null
        is ExportSource.Path -> when {
            !source.value.exists() -> ExportFailure.SOURCE_MISSING
            !source.value.isFile || !source.value.canRead() -> ExportFailure.SOURCE_UNREADABLE
            else -> null
        }
    }

    @MainThread
    private fun finish(result: ExportResult) {
        val completion = active?.completion ?: return
        active = null
        completion(result)
    }
}
