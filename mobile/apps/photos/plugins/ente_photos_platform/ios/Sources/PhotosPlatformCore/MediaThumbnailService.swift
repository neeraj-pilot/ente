import Foundation
#if SWIFT_PACKAGE
    import MediaLibraryCore
#endif
#if canImport(Photos) && canImport(UIKit)
    import Photos
    import UIKit

    @MainActor
    public final class MediaThumbnailService {
        private final class Operation {
            let asset: PHAsset
            let request: MediaThumbnailRequest
            let completion: (MediaThumbnailOutcome) -> Void
            var imageRequestID: PHImageRequestID?

            init(
                asset: PHAsset,
                request: MediaThumbnailRequest,
                completion: @escaping (MediaThumbnailOutcome) -> Void
            ) {
                self.asset = asset
                self.request = request
                self.completion = completion
            }
        }

        private let imageManager: PHCachingImageManager
        private let scheduler: MediaOperationScheduler
        private var operations: [String: Operation] = [:]
        private var drainingCancellations: [String: () -> Void] = [:]
        private var isDetached = false

        public init(
            imageManager: PHCachingImageManager = PHCachingImageManager(),
            scheduler: MediaOperationScheduler = MediaOperationScheduler()
        ) {
            self.imageManager = imageManager
            self.scheduler = scheduler
        }

        public func load(
            operationID: String,
            request: MediaThumbnailRequest,
            completion: @escaping (MediaThumbnailOutcome) -> Void
        ) {
            guard !isDetached else {
                completion(.failure(.platformFailure))
                return
            }
            guard operations[operationID] == nil else {
                completion(.failure(.busy))
                return
            }
            let authorizationStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
            switch authorizationStatus {
            case .authorized, .limited:
                break
            case .denied, .restricted, .notDetermined:
                completion(.failure(.permissionDenied))
                return
            @unknown default:
                completion(.failure(.platformFailure))
                return
            }
            guard
                let asset = PHAsset.fetchAssets(
                    withLocalIdentifiers: [request.assetID],
                    options: nil
                ).firstObject
            else {
                completion(.failure(authorizationStatus == .limited ? .permissionDenied : .assetNotFound))
                return
            }
            let expectedType: PHAssetMediaType = request.kind == .image ? .image : .video
            guard asset.mediaType == expectedType else {
                completion(.failure(.unsupportedFormat))
                return
            }

            operations[operationID] = Operation(asset: asset, request: request, completion: completion)
            let submission = scheduler.submit(operationID: operationID) { [weak self] in
                self?.start(operationID: operationID)
            }
            switch submission {
            case .started, .queued:
                break
            case .duplicate, .full:
                operations.removeValue(forKey: operationID)
                completion(.failure(.busy))
            }
        }

        public func cancel(operationID: String, onSettled: @escaping () -> Void) {
            guard let operation = operations.removeValue(forKey: operationID) else {
                onSettled()
                return
            }
            let cancellation = scheduler.cancel(operationID: operationID)
            if cancellation == .pending {
                operation.completion(.failure(.cancelled))
                onSettled()
                return
            }
            guard cancellation == .active else {
                onSettled()
                return
            }
            drainingCancellations[operationID] = onSettled
            if let imageRequestID = operation.imageRequestID {
                imageManager.cancelImageRequest(imageRequestID)
            }
            operation.completion(.failure(.cancelled))
        }

        public func detach() {
            guard !isDetached else { return }
            isDetached = true
            let requestIDs = operations.values.compactMap(\.imageRequestID)
            operations.removeAll()
            let settlementCallbacks = Array(drainingCancellations.values)
            drainingCancellations.removeAll()
            scheduler.removeAll()
            requestIDs.forEach(imageManager.cancelImageRequest)
            settlementCallbacks.forEach { $0() }
        }

        private func start(operationID: String) {
            guard let operation = operations[operationID] else {
                scheduler.finish(operationID: operationID)
                return
            }
            let options = PHImageRequestOptions()
            options.version = .current
            options.deliveryMode = .highQualityFormat
            options.resizeMode = .exact
            options.isSynchronous = false
            options.isNetworkAccessAllowed = operation.request.allowNetworkAccess
            let contentMode: PHImageContentMode = operation.request.fit == .contain ? .aspectFit : .aspectFill
            let targetSize = CGSize(width: operation.request.widthPx, height: operation.request.heightPx)
            let request = operation.request
            let imageRequestID = imageManager.requestImage(
                for: operation.asset,
                targetSize: targetSize,
                contentMode: contentMode,
                options: options
            ) { [weak self] image, info in
                if (info?[PHImageCancelledKey] as? Bool) == true {
                    Task { @MainActor [weak self] in
                        self?.finish(operationID: operationID, outcome: .failure(.cancelled))
                    }
                    return
                }
                if let error = info?[PHImageErrorKey] as? Error {
                    Task { @MainActor [weak self] in
                        self?.finish(
                            operationID: operationID,
                            outcome: .failure(Self.libraryError(error))
                        )
                    }
                    return
                }
                if (info?[PHImageResultIsDegradedKey] as? Bool) == true { return }
                guard let image else {
                    let isInCloud = (info?[PHImageResultIsInCloudKey] as? Bool) == true
                    let error: MediaLibraryError =
                        isInCloud && !request.allowNetworkAccess
                        ? .networkUnavailable
                        : .resourceUnavailable
                    Task { @MainActor [weak self] in
                        self?.finish(operationID: operationID, outcome: .failure(error))
                    }
                    return
                }
                let outcome = autoreleasepool { Self.encode(image: image, request: request) }
                Task { @MainActor [weak self] in
                    self?.finish(operationID: operationID, outcome: outcome)
                }
            }
            if let current = operations[operationID] {
                current.imageRequestID = imageRequestID
            } else {
                imageManager.cancelImageRequest(imageRequestID)
            }
        }

        private func finish(operationID: String, outcome: MediaThumbnailOutcome) {
            if let onSettled = drainingCancellations.removeValue(forKey: operationID) {
                scheduler.finish(operationID: operationID)
                onSettled()
                return
            }
            guard let operation = operations.removeValue(forKey: operationID) else { return }
            scheduler.finish(operationID: operationID)
            operation.completion(outcome)
        }

        private nonisolated static func encode(
            image: UIImage,
            request: MediaThumbnailRequest
        ) -> MediaThumbnailOutcome {
            guard image.size.width > 0, image.size.height > 0 else {
                return .failure(.unsupportedFormat)
            }
            let outputSize: CGSize
            let destination: CGRect
            switch request.fit {
            case .contain:
                let scale = min(
                    CGFloat(request.widthPx) / image.size.width,
                    CGFloat(request.heightPx) / image.size.height
                )
                outputSize = CGSize(
                    width: max(1, (image.size.width * scale).rounded()),
                    height: max(1, (image.size.height * scale).rounded())
                )
                destination = CGRect(origin: .zero, size: outputSize)
            case .cover:
                outputSize = CGSize(width: request.widthPx, height: request.heightPx)
                let scale = max(outputSize.width / image.size.width, outputSize.height / image.size.height)
                let drawnSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
                destination = CGRect(
                    x: (outputSize.width - drawnSize.width) / 2,
                    y: (outputSize.height - drawnSize.height) / 2,
                    width: drawnSize.width,
                    height: drawnSize.height
                )
            }
            let format = UIGraphicsImageRendererFormat.default()
            format.scale = 1
            format.opaque = true
            let normalized = UIGraphicsImageRenderer(size: outputSize, format: format).image { context in
                UIColor.black.setFill()
                context.fill(CGRect(origin: .zero, size: outputSize))
                image.draw(in: destination)
            }
            guard let data = normalized.jpegData(compressionQuality: CGFloat(request.quality) / 100),
                let thumbnail = try? MediaThumbnail(
                    jpegData: data,
                    widthPx: Int(outputSize.width),
                    heightPx: Int(outputSize.height)
                )
            else {
                return .failure(.unsupportedFormat)
            }
            return .success(thumbnail)
        }

        private nonisolated static func libraryError(_ error: Error) -> MediaLibraryError {
            let error = error as NSError
            guard error.domain == PHPhotosErrorDomain else { return .resourceUnavailable }
            switch error.code {
            case PHPhotosError.userCancelled.rawValue:
                return .cancelled
            case PHPhotosError.networkAccessRequired.rawValue:
                return .networkUnavailable
            case PHPhotosError.accessRestricted.rawValue, PHPhotosError.accessUserDenied.rawValue:
                return .permissionDenied
            case PHPhotosError.invalidResource.rawValue,
                PHPhotosError.requestNotSupportedForAsset.rawValue:
                return .unsupportedFormat
            default:
                if #available(iOS 16, *), error.code == PHPhotosError.networkError.rawValue {
                    return .networkUnavailable
                }
                return .resourceUnavailable
            }
        }
    }
#endif
