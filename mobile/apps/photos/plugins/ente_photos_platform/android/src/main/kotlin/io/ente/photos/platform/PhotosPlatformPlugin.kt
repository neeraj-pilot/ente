package io.ente.photos.platform

import io.ente.photos.platform.flutter.DeviceHealthChannelAdapter
import io.ente.photos.platform.flutter.DeviceTrashChannelAdapter
import io.flutter.embedding.engine.plugins.FlutterPlugin

class PhotosPlatformPlugin : FlutterPlugin {
    private val deviceHealthAdapter = DeviceHealthChannelAdapter()
    private val deviceTrashAdapter = DeviceTrashChannelAdapter()

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        deviceHealthAdapter.attach(binding)
        deviceTrashAdapter.attach(binding)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        deviceTrashAdapter.detach()
        deviceHealthAdapter.detach()
    }
}
