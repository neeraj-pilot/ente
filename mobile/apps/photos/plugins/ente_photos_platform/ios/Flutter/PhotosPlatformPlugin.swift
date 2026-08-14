@preconcurrency import Flutter
import Foundation

@MainActor
public final class PhotosPlatformPlugin: NSObject, @preconcurrency FlutterPlugin {
    private let deviceHealthAdapter: DeviceHealthChannelAdapter
    private let deviceTrashAdapter: DeviceTrashChannelAdapter

    private init(registrar: FlutterPluginRegistrar) {
        deviceHealthAdapter = DeviceHealthChannelAdapter(registrar: registrar)
        deviceTrashAdapter = DeviceTrashChannelAdapter(registrar: registrar)
        super.init()
    }

    public static func register(with registrar: FlutterPluginRegistrar) {
        registrar.publish(PhotosPlatformPlugin(registrar: registrar))
    }

    public func detachFromEngine(for registrar: FlutterPluginRegistrar) {
        deviceTrashAdapter.detach()
        deviceHealthAdapter.detach()
    }
}
