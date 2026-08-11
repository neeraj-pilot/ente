import Foundation

public enum MediaAssetKind: Equatable, Sendable {
    case image
    case video
}

public enum MediaThumbnailFit: Equatable, Sendable {
    case contain
    case cover
}

public struct MediaThumbnailRequest: Equatable, Sendable {
    public let assetID: String
    public let kind: MediaAssetKind
    public let widthPx: Int
    public let heightPx: Int
    public let fit: MediaThumbnailFit
    public let quality: Int
    public let allowNetworkAccess: Bool

    public init(
        assetID: String,
        kind: MediaAssetKind,
        widthPx: Int,
        heightPx: Int,
        fit: MediaThumbnailFit,
        quality: Int,
        allowNetworkAccess: Bool
    ) throws {
        guard !assetID.isEmpty, assetID.lengthOfBytes(using: .utf8) <= 1024,
            (1...2048).contains(widthPx),
            (1...2048).contains(heightPx),
            widthPx * heightPx <= 4_000_000,
            (1...100).contains(quality)
        else {
            throw MediaLibraryError.invalidRequest
        }
        self.assetID = assetID
        self.kind = kind
        self.widthPx = widthPx
        self.heightPx = heightPx
        self.fit = fit
        self.quality = quality
        self.allowNetworkAccess = allowNetworkAccess
    }
}

public struct MediaThumbnail: Equatable, Sendable {
    public let jpegData: Data
    public let widthPx: Int
    public let heightPx: Int

    public init(jpegData: Data, widthPx: Int, heightPx: Int) throws {
        guard jpegData.count >= 4,
            jpegData.starts(with: [0xff, 0xd8]),
            jpegData.suffix(2).elementsEqual([0xff, 0xd9]),
            (1...2048).contains(widthPx),
            (1...2048).contains(heightPx),
            widthPx * heightPx <= 4_000_000
        else {
            throw MediaLibraryError.platformFailure
        }
        self.jpegData = jpegData
        self.widthPx = widthPx
        self.heightPx = heightPx
    }
}

public enum MediaLibraryError: Error, Equatable, Sendable {
    case cancelled
    case permissionDenied
    case assetNotFound
    case resourceUnavailable
    case networkUnavailable
    case unsupportedFormat
    case invalidRequest
    case busy
    case unsupportedPlatform
    case platformFailure
}

public enum MediaThumbnailOutcome: Equatable, Sendable {
    case success(MediaThumbnail)
    case failure(MediaLibraryError)
}
