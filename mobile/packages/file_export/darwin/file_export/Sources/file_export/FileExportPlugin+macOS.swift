#if os(macOS)
    @preconcurrency import FlutterMacOS
    import AppKit

    public final class FileExportPlugin: NSObject, FlutterPlugin {
        @MainActor private lazy var exporter = Exporter()

        public static func register(with registrar: FlutterPluginRegistrar) {
            let channel = FlutterMethodChannel(
                name: "io.ente.file_export",
                binaryMessenger: registrar.messenger
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

            Task { @MainActor [weak self] in
                guard let self else {
                    result(
                        ExportResult.failed(.presentationFailed).channelValue
                    )
                    return
                }
                let window = NSApplication.shared.keyWindow ?? NSApplication.shared.mainWindow
                result(await exporter.export(request, window: window).channelValue)
            }
        }
    }
#endif
