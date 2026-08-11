@preconcurrency import Flutter
import Foundation

@MainActor
public final class PhotosPlatformPlugin: NSObject, @preconcurrency FlutterPlugin {
    private let deviceHealthAdapter: DeviceHealthChannelAdapter
    private let thumbnailAdapter: ThumbnailChannelAdapter

    private init(registrar: FlutterPluginRegistrar) {
        deviceHealthAdapter = DeviceHealthChannelAdapter(registrar: registrar)
        thumbnailAdapter = ThumbnailChannelAdapter(registrar: registrar)
        super.init()
    }

    public static func register(with registrar: FlutterPluginRegistrar) {
        registrar.publish(PhotosPlatformPlugin(registrar: registrar))
    }

    public func detachFromEngine(for registrar: FlutterPluginRegistrar) {
        thumbnailAdapter.detach()
        deviceHealthAdapter.detach()
    }
}
