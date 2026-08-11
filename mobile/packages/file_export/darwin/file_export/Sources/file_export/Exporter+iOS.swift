#if os(iOS)
    import Foundation
    import UIKit

    @MainActor
    public final class Exporter: NSObject, UIDocumentPickerDelegate {
        private struct ActiveExport {
            let picker: UIDocumentPickerViewController
            let stagingDirectory: URL?
            let continuation: CheckedContinuation<ExportResult, Never>
        }

        private var preparationID: UUID?
        private var active: ActiveExport?
        private var isClosed = false
        private let stagingRoot = FileManager.default.temporaryDirectory
            .appendingPathComponent("file-export", isDirectory: true)

        public override init() {
            super.init()
            try? FileManager.default.removeItem(at: stagingRoot)
        }

        public func export(_ request: ExportRequest, presenter: UIViewController) async
            -> ExportResult
        {
            guard !isClosed else { return .failed(.presentationFailed) }
            guard preparationID == nil, active == nil else { return .failed(.busy) }

            let identifier = UUID()
            preparationID = identifier
            let stagingRoot = stagingRoot
            let preparation = await Task.detached(priority: .userInitiated) {
                prepare(request, in: stagingRoot)
            }.value
            guard preparationID == identifier, !isClosed else {
                preparation.discard()
                return .failed(.presentationFailed)
            }
            preparationID = nil

            switch preparation {
            case let .failure(reason, message):
                return .failed(reason, message)
            case let .ready(url, stagingDirectory):
                let picker: UIDocumentPickerViewController
                if #available(iOS 14.0, *) {
                    picker = UIDocumentPickerViewController(
                        forExporting: [url],
                        asCopy: true
                    )
                } else {
                    picker = UIDocumentPickerViewController(
                        url: url,
                        in: .exportToService
                    )
                }
                picker.delegate = self
                picker.shouldShowFileExtensions = true
                return await withCheckedContinuation { continuation in
                    active = ActiveExport(
                        picker: picker,
                        stagingDirectory: stagingDirectory,
                        continuation: continuation
                    )
                    let visiblePresenter = presenter.visibleController
                    guard visiblePresenter.viewIfLoaded?.window != nil else {
                        finish(.failed(.presentationFailed))
                        return
                    }
                    visiblePresenter.present(picker, animated: true) { [weak self, weak picker] in
                        guard let self, let picker,
                            active?.picker === picker,
                            picker.presentingViewController != nil
                        else {
                            self?.finish(.failed(.presentationFailed))
                            return
                        }
                    }
                }
            }
        }

        public func close() {
            guard !isClosed else { return }
            isClosed = true
            preparationID = nil
            active?.picker.dismiss(animated: false)
            finish(.failed(.presentationFailed))
        }

        public func documentPicker(
            _ controller: UIDocumentPickerViewController,
            didPickDocumentsAt urls: [URL]
        ) {
            guard active?.picker === controller else { return }
            guard let destination = urls.first else {
                finish(.failed(.writeFailed))
                return
            }
            finish(.exported(destination.absoluteString))
        }

        public func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            guard active?.picker === controller else { return }
            finish(.cancelled)
        }

        private func finish(_ result: ExportResult) {
            guard let current = active else { return }
            active = nil
            current.stagingDirectory.map { try? FileManager.default.removeItem(at: $0) }
            current.continuation.resume(returning: result)
        }
    }

    private enum Preparation: Sendable {
        case ready(URL, stagingDirectory: URL?)
        case failure(ExportFailure, String?)

        func discard() {
            guard case let .ready(_, stagingDirectory) = self,
                let stagingDirectory
            else { return }
            try? FileManager.default.removeItem(at: stagingDirectory)
        }
    }

    private func prepare(_ request: ExportRequest, in stagingRoot: URL) -> Preparation {
        if let failure = request.source.failure {
            return .failure(failure, nil)
        }
        if case let .file(url) = request.source,
            url.lastPathComponent == request.fileName
        {
            return .ready(url, stagingDirectory: nil)
        }

        let directory = stagingRoot.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let stagedFile = directory.appendingPathComponent(request.fileName)
        do {
            try FileManager.default.createDirectory(
                at: directory,
                withIntermediateDirectories: true,
                attributes: [.posixPermissions: 0o700]
            )
            switch request.source {
            case let .data(data):
                try data.write(
                    to: stagedFile,
                    options: [.atomic, .completeFileProtection]
                )
            case let .file(source):
                do {
                    try FileManager.default.linkItem(at: source, to: stagedFile)
                } catch {
                    try FileManager.default.copyItem(at: source, to: stagedFile)
                }
            }
            return .ready(stagedFile, stagingDirectory: directory)
        } catch {
            try? FileManager.default.removeItem(at: directory)
            let reason = request.source.failure ?? .writeFailed
            return .failure(reason, error.localizedDescription)
        }
    }

    private extension UIViewController {
        var visibleController: UIViewController {
            if let presented = presentedViewController, !presented.isBeingDismissed {
                return presented.visibleController
            }
            if let navigation = self as? UINavigationController,
                let visible = navigation.visibleViewController
            {
                return visible.visibleController
            }
            if let tab = self as? UITabBarController,
                let selected = tab.selectedViewController
            {
                return selected.visibleController
            }
            return self
        }
    }
#endif
