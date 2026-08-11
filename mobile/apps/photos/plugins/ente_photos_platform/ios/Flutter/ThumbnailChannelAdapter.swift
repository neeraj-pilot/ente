@preconcurrency import Flutter
import Foundation

@MainActor
final class ThumbnailChannelAdapter {
    private let channel: FlutterMethodChannel
    private let service = MediaThumbnailService()
    private var isAttached = true

    init(registrar: FlutterPluginRegistrar) {
        channel = FlutterMethodChannel(
            name: Self.channelName,
            binaryMessenger: registrar.messenger()
        )
        channel.setMethodCallHandler { [weak self] call, result in
            Task { @MainActor [weak self] in
                self?.handle(call, result: result)
            }
        }
    }

    func detach() {
        isAttached = false
        channel.setMethodCallHandler(nil)
        service.detach()
    }

    private func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard isAttached else { return }
        switch call.method {
        case "thumbnail.load":
            load(arguments: call.arguments, result: result)
        case "thumbnail.cancel":
            cancel(arguments: call.arguments, result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func load(arguments: Any?, result: @escaping FlutterResult) {
        guard let values = strictArguments(arguments, keys: Self.loadKeys),
            let operationID = string(values["operationId"], maximumBytes: 128)
        else {
            result(flutterError(.invalidRequest))
            return
        }
        let request: MediaThumbnailRequest
        do {
            guard let assetID = string(values["assetId"], maximumBytes: 1024),
                let kind = mediaKind(values["kind"]),
                let widthPx = int(values["widthPx"]),
                let heightPx = int(values["heightPx"]),
                let fit = thumbnailFit(values["fit"]),
                let quality = int(values["quality"]),
                let allowNetworkAccess = bool(values["allowNetworkAccess"])
            else {
                throw MediaLibraryError.invalidRequest
            }
            request = try MediaThumbnailRequest(
                assetID: assetID,
                kind: kind,
                widthPx: widthPx,
                heightPx: heightPx,
                fit: fit,
                quality: quality,
                allowNetworkAccess: allowNetworkAccess
            )
        } catch {
            result(flutterError(.invalidRequest))
            return
        }
        service.load(operationID: operationID, request: request) { [weak self] outcome in
            guard let self, isAttached else { return }
            switch outcome {
            case .success(let thumbnail):
                result([
                    "jpegBytes": FlutterStandardTypedData(bytes: thumbnail.jpegData),
                    "widthPx": thumbnail.widthPx,
                    "heightPx": thumbnail.heightPx,
                ])
            case .failure(let error):
                result(flutterError(error))
            }
        }
    }

    private func cancel(arguments: Any?, result: @escaping FlutterResult) {
        guard let values = strictArguments(arguments, keys: Self.cancelKeys),
            let operationID = string(values["operationId"], maximumBytes: 128)
        else {
            result(flutterError(.invalidRequest))
            return
        }
        service.cancel(operationID: operationID) { [weak self] in
            guard let self, isAttached else { return }
            result(nil)
        }
    }

    private func strictArguments(_ arguments: Any?, keys: Set<String>) -> [String: Any]? {
        guard let values = arguments as? [String: Any], Set(values.keys) == keys else { return nil }
        return values
    }

    private func string(_ value: Any?, maximumBytes: Int) -> String? {
        guard let value = value as? String, !value.isEmpty,
            value.lengthOfBytes(using: .utf8) <= maximumBytes
        else {
            return nil
        }
        return value
    }

    private func int(_ value: Any?) -> Int? {
        guard let number = value as? NSNumber,
            CFGetTypeID(number) != CFBooleanGetTypeID(),
            number.doubleValue.rounded(.towardZero) == number.doubleValue,
            number.int64Value >= Int64(Int.min),
            number.int64Value <= Int64(Int.max)
        else {
            return nil
        }
        return number.intValue
    }

    private func bool(_ value: Any?) -> Bool? {
        guard let number = value as? NSNumber, CFGetTypeID(number) == CFBooleanGetTypeID() else {
            return nil
        }
        return number.boolValue
    }

    private func mediaKind(_ value: Any?) -> MediaAssetKind? {
        switch value as? String {
        case "image": .image
        case "video": .video
        default: nil
        }
    }

    private func thumbnailFit(_ value: Any?) -> MediaThumbnailFit? {
        switch value as? String {
        case "contain": .contain
        case "cover": .cover
        default: nil
        }
    }

    private func flutterError(_ error: MediaLibraryError) -> FlutterError {
        FlutterError(code: error.channelValue, message: nil, details: nil)
    }

    private static let channelName = "io.ente.photos.platform/media_library/commands.v1"
    private static let loadKeys: Set<String> = [
        "operationId",
        "assetId",
        "kind",
        "widthPx",
        "heightPx",
        "fit",
        "quality",
        "allowNetworkAccess",
    ]
    private static let cancelKeys: Set<String> = ["operationId"]
}

private extension MediaLibraryError {
    var channelValue: String {
        switch self {
        case .cancelled: "cancelled"
        case .permissionDenied: "permissionDenied"
        case .assetNotFound: "assetNotFound"
        case .resourceUnavailable: "resourceUnavailable"
        case .networkUnavailable: "networkUnavailable"
        case .unsupportedFormat: "unsupportedFormat"
        case .invalidRequest: "invalidRequest"
        case .busy: "busy"
        case .unsupportedPlatform: "unsupportedPlatform"
        case .platformFailure: "platformFailure"
        }
    }
}
