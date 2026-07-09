import "package:flutter/services.dart" show PlatformException;

const phPhotosResourceUnavailableReason =
    "iCloud Photos download failed (PHPhotosErrorDomain 3169)";

const _cloudPhotoLibraryErrorDomain = "CloudPhotoLibraryErrorDomain";
const _cloudPhotoLibraryUnavailableErrorCode = "1005";
const _phPhotosErrorDomain = "PHPhotosErrorDomain";
const _phPhotosNetworkErrorCode = "3169";
const _phPhotosUnsupportedResourceErrorCode = "3302";

bool isApplePhotosResourceUnavailableError(Object error) {
  return isPHPhotosNetworkError(error) ||
      _isApplePhotosError(
        error,
        _cloudPhotoLibraryErrorDomain,
        _cloudPhotoLibraryUnavailableErrorCode,
      );
}

bool isPHPhotosNetworkError(Object error) {
  return _isApplePhotosError(
    error,
    _phPhotosErrorDomain,
    _phPhotosNetworkErrorCode,
  );
}

bool isPHPhotosUnsupportedResourceError(Object error) {
  return _isApplePhotosError(
    error,
    _phPhotosErrorDomain,
    _phPhotosUnsupportedResourceErrorCode,
  );
}

bool _isApplePhotosError(Object error, String domain, String code) {
  if (error is! PlatformException) return false;
  return error.code == "$domain ($code)" ||
      (error.code.contains(domain) && error.code.contains(code)) ||
      (error.message?.contains("$domain error $code") ?? false);
}
