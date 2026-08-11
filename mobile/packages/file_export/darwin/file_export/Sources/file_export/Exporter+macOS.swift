#if os(macOS)
    import AppKit
    import Foundation
    import UniformTypeIdentifiers

    @MainActor
    public final class Exporter {
        private var activePanel: NSSavePanel?
        private var isExporting = false
        private var isClosed = false

        public init() {}

        public func export(_ request: ExportRequest, window: NSWindow?) async -> ExportResult {
            guard !isClosed else { return .failed(.presentationFailed) }
            guard !isExporting else { return .failed(.busy) }
            isExporting = true
            defer { isExporting = false }
            if let failure = request.source.failure { return .failed(failure) }

            let panel = NSSavePanel()
            panel.canCreateDirectories = true
            panel.nameFieldStringValue = request.fileName
            if #available(macOS 11.0, *), let contentType = UTType(mimeType: request.mimeType) {
                panel.allowedContentTypes = [contentType]
            }
            activePanel = panel
            let response = await withCheckedContinuation { continuation in
                if let window {
                    panel.beginSheetModal(for: window) { continuation.resume(returning: $0) }
                } else {
                    panel.begin { continuation.resume(returning: $0) }
                }
            }
            activePanel = nil
            guard !isClosed else { return .failed(.presentationFailed) }
            guard response == .OK else { return .cancelled }
            guard let destination = panel.url else { return .failed(.presentationFailed) }

            return await Task.detached(priority: .userInitiated) {
                write(request, to: destination)
            }.value
        }

        public func close() {
            guard !isClosed else { return }
            isClosed = true
            activePanel?.cancel(nil)
        }
    }

    private func write(_ request: ExportRequest, to destination: URL) -> ExportResult {
        if let failure = request.source.failure { return .failed(failure) }
        if case let .file(source) = request.source,
            source.standardizedFileURL == destination.standardizedFileURL
        {
            return .exported(destination.path)
        }

        let didAccess = destination.startAccessingSecurityScopedResource()
        defer {
            if didAccess { destination.stopAccessingSecurityScopedResource() }
        }

        let temporary = destination.deletingLastPathComponent().appendingPathComponent(
            ".\(destination.lastPathComponent).\(UUID().uuidString).tmp"
        )
        defer { try? FileManager.default.removeItem(at: temporary) }
        do {
            switch request.source {
            case let .data(data):
                try data.write(to: temporary, options: .atomic)
            case let .file(source):
                try FileManager.default.copyItem(at: source, to: temporary)
            }
            if FileManager.default.fileExists(atPath: destination.path) {
                _ = try FileManager.default.replaceItemAt(destination, withItemAt: temporary)
            } else {
                try FileManager.default.moveItem(at: temporary, to: destination)
            }
            return .exported(destination.path)
        } catch {
            return .failed(
                request.source.failure ?? .writeFailed,
                error.localizedDescription
            )
        }
    }
#endif
