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

        do {
            try write(request.source, to: destination)
            return .exported(destination.path)
        } catch {
            return .failed(
                request.source.failure ?? .writeFailed,
                error.localizedDescription
            )
        }
    }

    private func write(_ source: ExportSource, to destination: URL) throws {
        let manager = FileManager.default
        let directory = manager.temporaryDirectory
            .appendingPathComponent("file-export", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try manager.createDirectory(
            at: directory,
            withIntermediateDirectories: true,
            attributes: [.posixPermissions: 0o700]
        )
        defer { try? manager.removeItem(at: directory) }
        let staged = directory.appendingPathComponent(destination.lastPathComponent)
        guard
            manager.createFile(
                atPath: staged.path,
                contents: nil,
                attributes: [.posixPermissions: 0o600]
            )
        else {
            throw CocoaError(.fileWriteUnknown)
        }
        try writeContents(source, to: staged)
        try install(staged, at: destination)
    }

    private func writeContents(_ source: ExportSource, to destination: URL) throws {
        guard let output = OutputStream(url: destination, append: false) else {
            throw CocoaError(.fileWriteUnknown)
        }
        output.open()
        defer { output.close() }
        switch source {
        case let .data(data):
            try data.withUnsafeBytes { bytes in
                guard let address = bytes.bindMemory(to: UInt8.self).baseAddress else { return }
                try write(address, count: bytes.count, to: output)
            }
        case let .file(url):
            guard let input = InputStream(url: url) else {
                throw CocoaError(.fileReadUnknown)
            }
            input.open()
            defer { input.close() }
            var buffer = [UInt8](repeating: 0, count: 1024 * 1024)
            while true {
                let count = input.read(&buffer, maxLength: buffer.count)
                if count < 0 { throw input.streamError ?? CocoaError(.fileReadUnknown) }
                if count == 0 { break }
                try buffer.withUnsafeBufferPointer {
                    try write($0.baseAddress!, count: count, to: output)
                }
            }
        }
    }

    private func install(_ staged: URL, at destination: URL) throws {
        var coordinationError: NSError?
        var installationError: Error?
        NSFileCoordinator().coordinate(
            writingItemAt: destination,
            options: .forReplacing,
            error: &coordinationError
        ) { coordinatedDestination in
            do {
                if FileManager.default.fileExists(atPath: coordinatedDestination.path) {
                    _ = try FileManager.default.replaceItemAt(
                        coordinatedDestination,
                        withItemAt: staged
                    )
                } else {
                    try FileManager.default.moveItem(at: staged, to: coordinatedDestination)
                }
            } catch {
                installationError = error
            }
        }
        if let coordinationError { throw coordinationError }
        if let installationError { throw installationError }
    }

    private func write(_ bytes: UnsafePointer<UInt8>, count: Int, to output: OutputStream) throws {
        var offset = 0
        while offset < count {
            let written = output.write(bytes + offset, maxLength: count - offset)
            guard written > 0 else {
                throw output.streamError ?? CocoaError(.fileWriteUnknown)
            }
            offset += written
        }
    }
#endif
