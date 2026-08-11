import Foundation

#if os(iOS)
    import Flutter
#elseif os(macOS)
    import FlutterMacOS
#endif

extension ExportRequest {
    init(arguments: Any?) throws {
        guard let arguments = arguments as? [String: Any],
            let fileName = arguments["fileName"] as? String,
            let mimeType = arguments["mimeType"] as? String,
            let source = arguments["source"] as? [String: Any],
            let sourceType = source["type"] as? String
        else {
            throw ExportValidationError("Export request is invalid")
        }

        let exportSource: ExportSource
        switch sourceType {
        case "bytes":
            guard let bytes = source["bytes"] as? FlutterStandardTypedData else {
                throw ExportValidationError("Export bytes are missing")
            }
            exportSource = .data(bytes.data)
        case "file":
            guard let path = source["path"] as? String,
                path.hasPrefix("/"),
                !path.contains("\0")
            else {
                throw ExportValidationError("Export path is invalid")
            }
            exportSource = .file(URL(fileURLWithPath: path))
        default:
            throw ExportValidationError("Export source is invalid")
        }
        try self.init(
            fileName: fileName,
            mimeType: mimeType,
            source: exportSource
        )
    }
}

extension ExportResult {
    var channelValue: [String: String] {
        switch self {
        case let .exported(location):
            return ["status": "exported", "location": location]
        case .cancelled:
            return ["status": "cancelled"]
        case let .failed(reason, message):
            var value = ["status": "failed", "reason": reason.rawValue]
            value["message"] = message
            return value
        }
    }
}
