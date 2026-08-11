package io.ente.file_export

import android.app.Activity
import android.content.Intent
import io.ente.file_export.core.ExportFailure
import io.ente.file_export.core.ExportRequest
import io.ente.file_export.core.ExportResult
import io.ente.file_export.core.ExportSource
import io.ente.file_export.core.Exporter
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.io.File

class FileExportPlugin :
    FlutterPlugin,
    ActivityAware,
    MethodChannel.MethodCallHandler,
    PluginRegistry.ActivityResultListener {
    private lateinit var channel: MethodChannel
    private lateinit var exporter: Exporter
    private var activity: Activity? = null
    private var binding: ActivityPluginBinding? = null
    private var isAttached = false

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        isAttached = true
        exporter = Exporter(binding.applicationContext.contentResolver)
        channel = MethodChannel(binding.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method != "export") {
            result.notImplemented()
            return
        }
        val request = try {
            call.toRequest()
        } catch (error: IllegalArgumentException) {
            result.error("invalidRequest", error.message, null)
            return
        }
        val currentActivity = activity
        if (currentActivity == null) {
            result.success(
                ExportResult.Failed(ExportFailure.PRESENTATION_FAILED).channelValue
            )
            return
        }

        exporter.export(
            request,
            launch = { currentActivity.startActivityForResult(it, REQUEST_CODE) }
        ) { outcome ->
            if (isAttached) result.success(outcome.channelValue)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) return false
        when {
            resultCode == Activity.RESULT_CANCELED -> exporter.cancel()
            resultCode == Activity.RESULT_OK && data?.data != null -> exporter.selected(data.data!!)
            else -> exporter.selectionFailed()
        }
        return true
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.binding = binding
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        detachActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        detachActivity()
        exporter.selectionFailed()
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        isAttached = false
        channel.setMethodCallHandler(null)
        exporter.close()
    }

    private fun detachActivity() {
        binding?.removeActivityResultListener(this)
        binding = null
        activity = null
    }

    private companion object {
        const val CHANNEL = "io.ente.file_export"
        const val REQUEST_CODE = 0x4645
    }
}

private val ExportResult.channelValue: Map<String, String>
    get() = when (this) {
        is ExportResult.Exported -> mapOf(
            "status" to "exported",
            "location" to location
        )
        ExportResult.Cancelled -> mapOf("status" to "cancelled")
        is ExportResult.Failed -> buildMap {
            put("status", "failed")
            put("reason", reason.channelValue)
            message?.let { put("message", it) }
        }
    }

private val ExportFailure.channelValue: String
    get() = when (this) {
        ExportFailure.BUSY -> "busy"
        ExportFailure.SOURCE_MISSING -> "sourceMissing"
        ExportFailure.SOURCE_UNREADABLE -> "sourceUnreadable"
        ExportFailure.PRESENTATION_FAILED -> "presentationFailed"
        ExportFailure.WRITE_FAILED -> "writeFailed"
    }

private fun MethodCall.toRequest(): ExportRequest {
    val arguments = arguments as? Map<*, *>
        ?: throw IllegalArgumentException("Export request is missing")
    fun string(name: String): String = arguments[name] as? String
        ?: throw IllegalArgumentException("Export request has no $name")
    val source = arguments["source"] as? Map<*, *>
        ?: throw IllegalArgumentException("Export source is missing")
    val exportSource = when (source["type"]) {
        "bytes" -> ExportSource.Bytes(
            source["bytes"] as? ByteArray
                ?: throw IllegalArgumentException("Export bytes are missing")
        )
        "file" -> ExportSource.Path(
            File(
                source["path"] as? String
                    ?: throw IllegalArgumentException("Export path is missing")
            )
        )
        else -> throw IllegalArgumentException("Export source is invalid")
    }
    return ExportRequest(
        fileName = string("fileName"),
        mimeType = string("mimeType"),
        source = exportSource
    )
}
