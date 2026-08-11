package io.ente.photos.platform

import io.ente.photos.platform.flutter.DeviceHealthChannelAdapter
import io.ente.photos.platform.flutter.ThumbnailChannelAdapter
import io.flutter.embedding.engine.plugins.FlutterPlugin

class PhotosPlatformPlugin : FlutterPlugin {
    private val deviceHealthAdapter = DeviceHealthChannelAdapter()
    private val thumbnailAdapter = ThumbnailChannelAdapter()

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        deviceHealthAdapter.attach(binding)
        thumbnailAdapter.attach(binding)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        thumbnailAdapter.detach()
        deviceHealthAdapter.detach()
    }
}
