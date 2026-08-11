import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/services.dart';

enum MediaAssetKind { image, video }

enum MediaThumbnailFit { contain, cover }

enum MediaLibraryErrorCode {
  cancelled,
  permissionDenied,
  assetNotFound,
  resourceUnavailable,
  networkUnavailable,
  unsupportedFormat,
  invalidRequest,
  busy,
  unsupportedPlatform,
  platformFailure,
}

final class MediaLibraryException implements Exception {
  const MediaLibraryException(this.code);

  final MediaLibraryErrorCode code;

  @override
  String toString() => 'MediaLibraryException(${code.name})';
}

final class MediaThumbnailRequest {
  MediaThumbnailRequest({
    required this.assetId,
    required this.kind,
    required this.widthPx,
    required this.heightPx,
    required this.fit,
    required this.quality,
    required this.allowNetworkAccess,
  }) {
    if (assetId.isEmpty || utf8.encode(assetId).length > _maximumAssetIdBytes) {
      throw ArgumentError('assetId must contain 1–1024 UTF-8 bytes');
    }
    if (widthPx <= 0 || widthPx > _maximumDimensionPx) {
      throw ArgumentError.value(
        widthPx,
        'widthPx',
        'must be between 1 and 2048',
      );
    }
    if (heightPx <= 0 || heightPx > _maximumDimensionPx) {
      throw ArgumentError.value(
        heightPx,
        'heightPx',
        'must be between 1 and 2048',
      );
    }
    if (widthPx * heightPx > _maximumPixelCount) {
      throw ArgumentError(
        'Thumbnail dimensions must not exceed 4,000,000 pixels',
      );
    }
    if (quality < 1 || quality > 100) {
      throw ArgumentError.value(
        quality,
        'quality',
        'must be between 1 and 100',
      );
    }
  }

  static const _maximumAssetIdBytes = 1024;
  static const _maximumDimensionPx = 2048;
  static const _maximumPixelCount = 4000000;

  final String assetId;
  final MediaAssetKind kind;
  final int widthPx;
  final int heightPx;
  final MediaThumbnailFit fit;
  final int quality;
  final bool allowNetworkAccess;
}

final class MediaThumbnail {
  MediaThumbnail._({
    required this.jpegBytes,
    required this.widthPx,
    required this.heightPx,
  });

  final Uint8List jpegBytes;
  final int widthPx;
  final int heightPx;
}

final class MediaThumbnailTask {
  MediaThumbnailTask._({
    required this.result,
    required Future<void> Function() cancel,
  }) : _cancel = cancel;

  final Future<MediaThumbnail> result;
  final Future<void> Function() _cancel;
  Future<void>? _cancellation;

  Future<void> cancel() => _cancellation ??= _cancel();
}

final class MediaLibraryClient {
  MediaLibraryClient({MethodChannel? methodChannel})
    : _methodChannel = methodChannel ?? const MethodChannel(_channelName);

  static final instance = MediaLibraryClient();
  static const _channelName =
      'io.ente.photos.platform/media_library/commands.v1';
  static final Random _random = Random.secure();
  static int _operationSequence = 0;

  final MethodChannel _methodChannel;

  MediaThumbnailTask loadThumbnail(MediaThumbnailRequest request) {
    final operationId = _newOperationId();
    final result = _load(operationId, request);
    return MediaThumbnailTask._(
      result: result,
      cancel: () => _cancel(operationId),
    );
  }

  Future<MediaThumbnail> _load(
    String operationId,
    MediaThumbnailRequest request,
  ) async {
    try {
      final value = await _methodChannel
          .invokeMethod<Object?>('thumbnail.load', <String, Object>{
            'operationId': operationId,
            'assetId': request.assetId,
            'kind': request.kind.name,
            'widthPx': request.widthPx,
            'heightPx': request.heightPx,
            'fit': request.fit.name,
            'quality': request.quality,
            'allowNetworkAccess': request.allowNetworkAccess,
          });
      try {
        return _decodeThumbnail(value);
      } on FormatException {
        throw const MediaLibraryException(
          MediaLibraryErrorCode.platformFailure,
        );
      }
    } on PlatformException catch (error) {
      throw MediaLibraryException(_errorCode(error.code));
    } on MissingPluginException {
      throw const MediaLibraryException(
        MediaLibraryErrorCode.unsupportedPlatform,
      );
    }
  }

  Future<void> _cancel(String operationId) async {
    try {
      final value = await _methodChannel.invokeMethod<Object?>(
        'thumbnail.cancel',
        <String, Object>{'operationId': operationId},
      );
      if (value != null) {
        throw const MediaLibraryException(
          MediaLibraryErrorCode.platformFailure,
        );
      }
    } on PlatformException catch (error) {
      throw MediaLibraryException(_errorCode(error.code));
    } on MissingPluginException {
      throw const MediaLibraryException(
        MediaLibraryErrorCode.unsupportedPlatform,
      );
    }
  }

  static String _newOperationId() {
    final randomBytes = List<int>.generate(16, (_) => _random.nextInt(256));
    final random = base64UrlEncode(randomBytes).replaceAll('=', '');
    final sequence = _operationSequence = (_operationSequence + 1) & 0x7fffffff;
    return '${DateTime.now().microsecondsSinceEpoch}-$sequence-$random';
  }
}

MediaThumbnail _decodeThumbnail(Object? value) {
  if (value is! Map<dynamic, dynamic> ||
      value.keys.any((key) => key is! String) ||
      value.keys.toSet().difference(_thumbnailResponseKeys).isNotEmpty ||
      _thumbnailResponseKeys.difference(value.keys.toSet()).isNotEmpty) {
    throw const FormatException('Thumbnail response has an invalid shape');
  }
  final bytes = value['jpegBytes'];
  final width = value['widthPx'];
  final height = value['heightPx'];
  if (bytes is! Uint8List || width is! int || height is! int) {
    throw const FormatException('Thumbnail response has invalid value types');
  }
  if (bytes.length < 4 ||
      bytes[0] != 0xff ||
      bytes[1] != 0xd8 ||
      bytes[bytes.length - 2] != 0xff ||
      bytes[bytes.length - 1] != 0xd9) {
    throw const FormatException('Thumbnail response is not JPEG data');
  }
  if (width <= 0 ||
      width > 2048 ||
      height <= 0 ||
      height > 2048 ||
      width * height > 4000000) {
    throw const FormatException('Thumbnail response has invalid dimensions');
  }
  return MediaThumbnail._(
    jpegBytes: Uint8List.fromList(bytes),
    widthPx: width,
    heightPx: height,
  );
}

const _thumbnailResponseKeys = <Object>{'jpegBytes', 'widthPx', 'heightPx'};

MediaLibraryErrorCode _errorCode(String value) {
  for (final code in MediaLibraryErrorCode.values) {
    if (code.name == value) return code;
  }
  return MediaLibraryErrorCode.platformFailure;
}
