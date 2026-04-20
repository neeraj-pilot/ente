package io.ente.entegram.core.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val LOG_FILE_NAME = "entegram.log"
    private const val MAX_LOG_BYTES = 512 * 1024
    private const val RETAINED_LOG_BYTES = 256 * 1024
    private const val DEFAULT_SNAPSHOT_BYTES = 96 * 1024

    private val lock = Any()

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            val logsDir = File(context.filesDir, "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            logFile = File(logsDir, LOG_FILE_NAME)
            rotateIfNeededLocked()
            installCrashLogger()
        }
        i("App", "logger initialized")
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        write(Log.DEBUG, "DEBUG", tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        write(Log.INFO, "INFO", tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        write(Log.WARN, "WARN", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        write(Log.ERROR, "ERROR", tag, message, throwable)
    }

    fun snapshot(maxBytes: Int = DEFAULT_SNAPSHOT_BYTES): String {
        val file = logFile ?: return "Logger is not initialized yet."
        if (!file.exists()) {
            return "No logs yet."
        }
        return synchronized(lock) {
            try {
                val bytes = file.readBytes()
                if (bytes.isEmpty()) {
                    "No logs yet."
                } else if (bytes.size <= maxBytes) {
                    bytes.toString(Charsets.UTF_8)
                } else {
                    "Showing the last ${maxBytes / 1024} KB of logs.\n\n" +
                        bytes.copyOfRange(bytes.size - maxBytes, bytes.size)
                            .toString(Charsets.UTF_8)
                }
            } catch (error: Exception) {
                "Failed to read logs: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    private fun write(
        priority: Int,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val fullTag = "EnteGram/$tag"
        if (throwable == null) {
            platformLog(priority, fullTag, message)
        } else {
            platformLog(priority, fullTag, "$message\n${stackTraceString(throwable)}")
        }

        synchronized(lock) {
            val file = logFile ?: return@synchronized
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(Date())
                val stackTrace = throwable?.let { "\n${stackTraceString(it)}" }.orEmpty()
                file.appendText("$timestamp $level $tag: $message$stackTrace\n")
                rotateIfNeededLocked()
            } catch (_: Exception) {
                // Logging must never crash the app.
            }
        }
    }

    private fun platformLog(priority: Int, tag: String, message: String) {
        try {
            Log.println(priority, tag, message)
        } catch (_: RuntimeException) {
            // Local JVM tests do not provide android.util.Log.
        }
    }

    private fun stackTraceString(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun installCrashLogger() {
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler is LoggingUncaughtExceptionHandler) {
            return
        }
        Thread.setDefaultUncaughtExceptionHandler(
            LoggingUncaughtExceptionHandler(currentHandler),
        )
    }

    private fun rotateIfNeededLocked() {
        val file = logFile ?: return
        if (!file.exists() || file.length() <= MAX_LOG_BYTES) {
            return
        }
        try {
            val bytes = file.readBytes()
            val retainedStart = (bytes.size - RETAINED_LOG_BYTES).coerceAtLeast(0)
            file.writeBytes(bytes.copyOfRange(retainedStart, bytes.size))
        } catch (_: Exception) {
            // Logging must never crash the app.
        }
    }

    private class LoggingUncaughtExceptionHandler(
        private val delegate: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            e("Crash", "uncaught exception on ${thread.name}", throwable)
            delegate?.uncaughtException(thread, throwable)
        }
    }
}
