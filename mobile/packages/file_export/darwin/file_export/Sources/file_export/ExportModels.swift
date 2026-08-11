import Foundation

public enum ExportSource: Sendable {
    case data(Data)
    case file(URL)
}

public struct ExportRequest: Sendable {
    public let fileName: String
    public let mimeType: String
    public let source: ExportSource

    public init(fileName: String, mimeType: String, source: ExportSource) throws {
        guard fileName.isValidFileName else {
            throw ExportValidationError("Invalid file name")
        }
        guard
            mimeType.range(
                of: #"^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$"#,
                options: .regularExpression
            ) != nil
        else {
            throw ExportValidationError("Invalid MIME type")
        }
        if case let .file(url) = source,
            (!url.isFileURL || !url.path.hasPrefix("/"))
        {
            throw ExportValidationError("Invalid source path")
        }
        self.fileName = fileName
        self.mimeType = mimeType
        self.source = source
    }
}

public enum ExportResult: Sendable {
    case exported(String)
    case cancelled
    case failed(ExportFailure, String? = nil)
}

public enum ExportFailure: String, Sendable {
    case busy
    case sourceMissing
    case sourceUnreadable
    case presentationFailed
    case writeFailed
}

public struct ExportValidationError: LocalizedError {
    public let errorDescription: String?

    public init(_ message: String) {
        errorDescription = message
    }
}

extension ExportSource {
    var failure: ExportFailure? {
        guard case let .file(url) = self else { return nil }
        var isDirectory = ObjCBool(false)
        guard
            FileManager.default.fileExists(
                atPath: url.path,
                isDirectory: &isDirectory
            )
        else {
            return .sourceMissing
        }
        return !isDirectory.boolValue && FileManager.default.isReadableFile(atPath: url.path)
            ? nil
            : .sourceUnreadable
    }
}

private extension String {
    var isValidFileName: Bool {
        !trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && self != "."
            && self != ".."
            && unicodeScalars.allSatisfy {
                $0.value >= 32 && !"\\/:*?\"<>|".unicodeScalars.contains($0)
            }
    }
}
