package io.ente.photos.platform.flutter

import android.os.Handler
import android.os.Looper
import io.ente.photos.platform.media.MediaAssetKind
import io.ente.photos.platform.media.MediaLibraryError
import io.ente.photos.platform.media.MediaThumbnailFit
import io.ente.photos.platform.media.MediaThumbnailOutcome
import io.ente.photos.platform.media.MediaThumbnailRequest
import io.ente.photos.platform.media.MediaThumbnailService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

internal class ThumbnailChannelAdapter : MethodChannel.MethodCallHandler {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var channel: MethodChannel
    private lateinit var service: MediaThumbnailService
    private var attached = false

    fun attach(binding: FlutterPlugin.FlutterPluginBinding) {
        service = MediaThumbnailService(binding.applicationContext)
        channel = MethodChannel(binding.binaryMessenger, CHANNEL_NAME)
        channel.setMethodCallHandler(this)
        attached = true
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "thumbnail.load" -> load(call.arguments, result)
            "thumbnail.cancel" -> cancel(call.arguments, result)
            else -> result.notImplemented()
        }
    }

    fun detach() {
        attached = false
        channel.setMethodCallHandler(null)
        service.close()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun load(arguments: Any?, result: MethodChannel.Result) {
        val values = strictArguments(arguments, LOAD_KEYS)
            ?: return result.error(MediaLibraryError.INVALID_REQUEST.channelValue, null, null)
        val operationId = values.string("operationId", 128)
        val request = runCatching {
            MediaThumbnailRequest(
                assetId = requireNotNull(values.string("assetId", 1024)),
                kind = when (values["kind"]) {
                    "image" -> MediaAssetKind.IMAGE
                    "video" -> MediaAssetKind.VIDEO
                    else -> error("Invalid media kind")
                },
                widthPx = requireNotNull(values.int("widthPx")),
                heightPx = requireNotNull(values.int("heightPx")),
                fit = when (values["fit"]) {
                    "contain" -> MediaThumbnailFit.CONTAIN
                    "cover" -> MediaThumbnailFit.COVER
                    else -> error("Invalid thumbnail fit")
                },
                quality = requireNotNull(values.int("quality")),
                allowNetworkAccess = requireNotNull(values["allowNetworkAccess"] as? Boolean),
            )
        }.getOrNull()
        if (operationId == null || request == null) {
            result.error(MediaLibraryError.INVALID_REQUEST.channelValue, null, null)
            return
        }
        service.load(operationId, request) { outcome ->
            mainHandler.post {
                if (!attached) return@post
                when (outcome) {
                    is MediaThumbnailOutcome.Success -> result.success(
                        mapOf(
                            "jpegBytes" to outcome.thumbnail.jpegBytes,
                            "widthPx" to outcome.thumbnail.widthPx,
                            "heightPx" to outcome.thumbnail.heightPx,
                        ),
                    )

                    is MediaThumbnailOutcome.Failure ->
                        result.error(outcome.error.channelValue, null, null)
                }
            }
        }
    }

    private fun cancel(arguments: Any?, result: MethodChannel.Result) {
        val values = strictArguments(arguments, CANCEL_KEYS)
        val operationId = values?.string("operationId", 128)
        if (operationId == null) {
            result.error(MediaLibraryError.INVALID_REQUEST.channelValue, null, null)
            return
        }
        service.cancel(operationId) {
            mainHandler.post {
                if (attached) result.success(null)
            }
        }
    }

    private fun strictArguments(arguments: Any?, keys: Set<String>): Map<*, *>? {
        val values = arguments as? Map<*, *> ?: return null
        if (values.keys.any { it !is String } || values.keys != keys) return null
        return values
    }

    private fun Map<*, *>.string(key: String, maximumBytes: Int): String? =
        (this[key] as? String)?.takeIf {
            it.isNotEmpty() && it.toByteArray(Charsets.UTF_8).size <= maximumBytes
        }

    private fun Map<*, *>.int(key: String): Int? = this[key] as? Int

    private val MediaLibraryError.channelValue: String
        get() = when (this) {
            MediaLibraryError.CANCELLED -> "cancelled"
            MediaLibraryError.PERMISSION_DENIED -> "permissionDenied"
            MediaLibraryError.ASSET_NOT_FOUND -> "assetNotFound"
            MediaLibraryError.RESOURCE_UNAVAILABLE -> "resourceUnavailable"
            MediaLibraryError.NETWORK_UNAVAILABLE -> "networkUnavailable"
            MediaLibraryError.UNSUPPORTED_FORMAT -> "unsupportedFormat"
            MediaLibraryError.INVALID_REQUEST -> "invalidRequest"
            MediaLibraryError.BUSY -> "busy"
            MediaLibraryError.UNSUPPORTED_PLATFORM -> "unsupportedPlatform"
            MediaLibraryError.PLATFORM_FAILURE -> "platformFailure"
        }

    private companion object {
        const val CHANNEL_NAME = "io.ente.photos.platform/media_library/commands.v1"
        val LOAD_KEYS = setOf(
            "operationId",
            "assetId",
            "kind",
            "widthPx",
            "heightPx",
            "fit",
            "quality",
            "allowNetworkAccess",
        )
        val CANCEL_KEYS = setOf("operationId")
    }
}
