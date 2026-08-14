package io.ente.photos.platform.flutter

import android.os.Handler
import android.os.Looper
import io.ente.photos.platform.devicetrash.DeviceTrashResult
import io.ente.photos.platform.devicetrash.DeviceTrashService
import io.ente.photos.platform.devicetrash.TrashedMedia
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class DeviceTrashChannelAdapter : MethodChannel.MethodCallHandler {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var channel: MethodChannel
    private lateinit var executor: ExecutorService
    private lateinit var service: DeviceTrashService
    private var isAttached = false

    fun attach(binding: FlutterPlugin.FlutterPluginBinding) {
        service = DeviceTrashService(binding.applicationContext.contentResolver)
        executor = Executors.newSingleThreadExecutor()
        channel = MethodChannel(binding.binaryMessenger, CHANNEL_NAME)
        channel.setMethodCallHandler(this)
        isAttached = true
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getFiles" -> getFiles(result)
            else -> result.notImplemented()
        }
    }

    fun detach() {
        isAttached = false
        channel.setMethodCallHandler(null)
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun getFiles(result: MethodChannel.Result) {
        executor.execute {
            try {
                when (val response = service.getFiles()) {
                    is DeviceTrashResult.Success -> {
                        val files = response.files.map { it.toChannelMap() }
                        postResult { result.success(files) }
                    }

                    DeviceTrashResult.Unsupported ->
                        postResult {
                            result.error(
                                "unsupported_platform",
                                "Device trash requires Android 11 or newer",
                                null,
                            )
                        }
                }
            } catch (error: SecurityException) {
                postResult { result.error("permission_denied", error.message, null) }
            } catch (error: RuntimeException) {
                postResult { result.error("query_failed", error.message, null) }
            }
        }
    }

    private fun postResult(action: () -> Unit) {
        mainHandler.post {
            if (isAttached) action()
        }
    }

    private fun TrashedMedia.toChannelMap(): Map<String, Any?> =
        mapOf(
            "mediaStoreId" to mediaStoreId,
            "volumeName" to volumeName,
            "expiresAtUs" to TimeUnit.SECONDS.toMicros(expiresAtEpochSeconds),
            "bucketName" to bucketName,
        )

    private companion object {
        const val CHANNEL_NAME = "io.ente.photos.platform/device_trash/commands.v1"
    }
}
