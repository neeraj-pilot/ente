@preconcurrency import Flutter
import Foundation

@MainActor
final class DeviceTrashChannelAdapter {
    private let channel: FlutterMethodChannel
    private var isAttached = true

    init(registrar: FlutterPluginRegistrar) {
        channel = FlutterMethodChannel(
            name: Self.channelName,
            binaryMessenger: registrar.messenger()
        )
        channel.setMethodCallHandler { [weak self] call, result in
            self?.handle(call, result: result)
        }
    }

    func detach() {
        guard isAttached else { return }
        isAttached = false
        channel.setMethodCallHandler(nil)
    }

    private func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard isAttached else { return }
        switch call.method {
        case "getFiles":
            result(
                FlutterError(
                    code: "unsupported_platform",
                    message: "Device trash is unavailable on iOS",
                    details: nil
                )
            )
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private static let channelName = "io.ente.photos.platform/device_trash/commands.v1"
}
