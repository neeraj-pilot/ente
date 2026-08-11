#if os(iOS)
    @preconcurrency import Flutter
    import UIKit

    @MainActor
    public final class FileExportPlugin: NSObject, @preconcurrency FlutterPlugin {
        private let exporter = Exporter()
        private var isDetached = false

        public static func register(with registrar: FlutterPluginRegistrar) {
            let channel = FlutterMethodChannel(
                name: "io.ente.file_export",
                binaryMessenger: registrar.messenger()
            )
            let instance = FileExportPlugin()
            registrar.addMethodCallDelegate(instance, channel: channel)
            registrar.publish(instance)
        }

        public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
            guard call.method == "export" else {
                result(FlutterMethodNotImplemented)
                return
            }
            let request: ExportRequest
            do {
                request = try ExportRequest(arguments: call.arguments)
            } catch {
                result(
                    FlutterError(
                        code: "invalidRequest",
                        message: error.localizedDescription,
                        details: nil
                    ))
                return
            }
            guard let presenter = UIApplication.shared.activePresenter else {
                result(ExportResult.failed(.presentationFailed).channelValue)
                return
            }

            Task { [weak self] in
                guard let self else { return }
                let value = await exporter.export(request, presenter: presenter).channelValue
                guard !isDetached else { return }
                result(value)
            }
        }

        public func detachFromEngine(for _: FlutterPluginRegistrar) {
            isDetached = true
            exporter.close()
        }
    }

    private extension UIApplication {
        var activePresenter: UIViewController? {
            connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .filter { $0.activationState == .foregroundActive }
                .flatMap(\.windows)
                .first(where: \.isKeyWindow)?
                .rootViewController
        }
    }
#endif
