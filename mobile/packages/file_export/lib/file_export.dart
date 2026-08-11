import 'package:flutter/services.dart';

sealed class FileExportResult {
  const FileExportResult();
}

final class FileExported extends FileExportResult {
  const FileExported(this.location);

  /// The destination path or URI supplied by the platform.
  final String location;
}

final class FileExportCancelled extends FileExportResult {
  const FileExportCancelled();
}

final class FileExportFailed extends FileExportResult {
  const FileExportFailed(this.reason, [this.message]);

  final FileExportFailure reason;
  final String? message;
}

enum FileExportFailure {
  busy,
  sourceMissing,
  sourceUnreadable,
  presentationFailed,
  writeFailed,
  unsupportedPlatform,
  platformError,
}

final class FileExporter {
  const FileExporter({
    MethodChannel channel = const MethodChannel('io.ente.file_export'),
  }) : _channel = channel;

  final MethodChannel _channel;

  Future<FileExportResult> exportBytes({
    required String fileName,
    required String mimeType,
    required Uint8List bytes,
  }) {
    return _export(
      fileName: fileName,
      mimeType: mimeType,
      source: <String, Object>{'type': 'bytes', 'bytes': bytes},
    );
  }

  Future<FileExportResult> exportFile({
    required String fileName,
    required String mimeType,
    required String path,
  }) {
    if (path.isEmpty || path.contains('\u0000')) {
      throw ArgumentError.value(path, 'path', 'must identify a file');
    }
    return _export(
      fileName: fileName,
      mimeType: mimeType,
      source: <String, Object>{'type': 'file', 'path': path},
    );
  }

  Future<FileExportResult> _export({
    required String fileName,
    required String mimeType,
    required Map<String, Object> source,
  }) async {
    _validateFileName(fileName);
    if (!_mimeType.hasMatch(mimeType)) {
      throw ArgumentError.value(mimeType, 'mimeType', 'is invalid');
    }

    try {
      final response = await _channel.invokeMapMethod<String, Object?>(
        'export',
        <String, Object>{
          'fileName': fileName,
          'mimeType': mimeType,
          'source': source,
        },
      );
      return _decode(response);
    } on MissingPluginException catch (error) {
      return FileExportFailed(
        FileExportFailure.unsupportedPlatform,
        error.message,
      );
    } on PlatformException catch (error) {
      return FileExportFailed(
        _failureByName[error.code] ?? FileExportFailure.platformError,
        error.message,
      );
    }
  }
}

FileExportResult _decode(Map<String, Object?>? response) {
  if (response == null) {
    return const FileExportFailed(
      FileExportFailure.platformError,
      'The platform returned no result',
    );
  }
  switch (response['status']) {
    case 'exported':
      final location = response['location'];
      if (location is String && location.isNotEmpty) {
        return FileExported(location);
      }
      return const FileExportFailed(
        FileExportFailure.platformError,
        'The platform returned no export location',
      );
    case 'cancelled':
      return const FileExportCancelled();
    case 'failed':
      final reason = response['reason'];
      final message = response['message'];
      return FileExportFailed(
        reason is String
            ? _failureByName[reason] ?? FileExportFailure.platformError
            : FileExportFailure.platformError,
        message is String ? message : null,
      );
  }
  return const FileExportFailed(
    FileExportFailure.platformError,
    'The platform returned an invalid result',
  );
}

void _validateFileName(String fileName) {
  if (fileName.trim().isEmpty ||
      fileName == '.' ||
      fileName == '..' ||
      _invalidFileNameCharacter.hasMatch(fileName)) {
    throw ArgumentError.value(fileName, 'fileName', 'must be a file name');
  }
}

final _mimeType = RegExp(r'^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$');
final _invalidFileNameCharacter = RegExp(r'[\\/:*?"<>|\x00-\x1F]');

const _failureByName = <String, FileExportFailure>{
  'busy': FileExportFailure.busy,
  'sourceMissing': FileExportFailure.sourceMissing,
  'sourceUnreadable': FileExportFailure.sourceUnreadable,
  'presentationFailed': FileExportFailure.presentationFailed,
  'writeFailed': FileExportFailure.writeFailed,
  'unsupportedPlatform': FileExportFailure.unsupportedPlatform,
};
