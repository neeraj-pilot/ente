import "dart:io";

import "package:dio/dio.dart";
import "package:ente_crypto/ente_crypto.dart";
import "package:ente_feature_flag/ente_feature_flag.dart";
import "package:ente_pure_utils/ente_pure_utils.dart";
import "package:flutter/foundation.dart";
import "package:logging/logging.dart";
import "package:photos/core/constants.dart";
import "package:photos/core/errors.dart";
import "package:photos/db/upload_locks_db.dart";
import "package:photos/gateways/files/file_upload_gateway.dart";
import "package:photos/module/upload/model/multipart.dart";
import "package:photos/module/upload/model/xml.dart";
import "package:photos/service_locator.dart";
import "package:photos/services/collections_service.dart";

bool shouldDiscardMultipartUpload(Object error, {required bool duringCommit}) {
  if (error is MultiPartFileMissingError ||
      error is MultiPartError ||
      error is EncSizeMismatchError ||
      error is BadMD5DigestError) {
    return true;
  }
  // A resume would commit the same object again.
  return duringCommit && error is DioException;
}

class MultiPartUploader {
  static const _maximumPartUploadAttempts = 3;

  final Dio _s3Dio;
  final UploadLocksDB _db;
  final FlagService _featureFlagService;
  final FileUploadGateway _gateway;
  late final Logger _logger = Logger("MultiPartUploader");

  MultiPartUploader(
    this._s3Dio,
    this._db,
    this._featureFlagService,
    this._gateway,
  );

  Future<FileEncryptResult> getEncryptionResult(
    String localId,
    String fileHash,
    int collectionID,
    String encFileName,
  ) async {
    final collectionKey = CollectionsService.instance.getCollectionKey(
      collectionID,
    );
    final result = await _db.getFileEncryptionData(
      localId,
      fileHash,
      collectionID,
      encFileName,
    );
    final encryptedFileKey = CryptoUtil.base642bin(result.encryptedFileKey);
    final fileNonce = CryptoUtil.base642bin(result.fileNonce);

    final encryptKeyNonce = CryptoUtil.base642bin(result.keyNonce);

    final multipartInfo = await _db.getCachedLinks(
      localId,
      fileHash,
      collectionID,
      encFileName,
    );

    return FileEncryptResult(
      key: CryptoUtil.decryptSync(
        encryptedFileKey,
        collectionKey,
        encryptKeyNonce,
      ),
      header: fileNonce,
      fileMd5: multipartInfo.fileMd5,
      partMd5s: multipartInfo.partMd5s,
      partSize: multipartInfo.partSize,
    );
  }

  int get multipartPartSizeForUpload {
    return multipartPartSize;
  }

  bool get _shouldUseCFUploadProxy =>
      !_featureFlagService.disableCFWorker &&
      (localSettings.cfUploadProxyEnabled ??
          _featureFlagService.cloudflareUploadWorker) &&
      endpointConfig.isProduction;

  int calculatePartCount(int fileSize) {
    if (!_featureFlagService.enableMobMultiPart) return 1;
    if (!localSettings.userEnabledMultiplePart) return 1;

    final partCount = (fileSize / multipartPartSizeForUpload).ceil();
    return partCount;
  }

  Future<MultipartUploadURLs> getMultipartUploadURLs({
    required int count,
    required int contentLength,
    required int partLength,
    required List<String> partMd5s,
  }) async {
    try {
      final recomputedCount = (contentLength / partLength).ceil();

      final urls = await _gateway.getMultipartUploadUrl(
        contentLength: contentLength,
        partLength: partLength,
        partMd5s: partMd5s,
      );
      if (urls.partsURLs.length != recomputedCount ||
          count != recomputedCount) {
        _logger.severe(
          'Multipart URL count mismatch. Requested count=$count, '
          'expected (from sizes)=$recomputedCount (contentLength=$contentLength, partLength=$partLength), '
          'received=${urls.partsURLs.length} for objectKey=${urls.objectKey}',
        );
        throw MultiPartError(
          'multipart url count mismatch: expected $recomputedCount, count $count, got ${urls.partsURLs.length}',
        );
      }

      return urls;
    } on Exception catch (e) {
      _logger.severe('failed to get multipart url', e);
      rethrow;
    }
  }

  Future<void> createTableEntry(
    String localId,
    String fileHash,
    int collectionID,
    MultipartUploadURLs urls,
    String encryptedFileName,
    int fileSize,
    Uint8List fileKey,
    Uint8List fileNonce, {
    String? fileMd5,
    List<String>? partMd5s,
  }) async {
    final collectionKey = CollectionsService.instance.getCollectionKey(
      collectionID,
    );

    final encryptedResult = CryptoUtil.encryptSync(fileKey, collectionKey);

    await _db.createTrackUploadsEntry(
      localId,
      fileHash,
      collectionID,
      urls,
      encryptedFileName,
      fileSize,
      CryptoUtil.bin2base64(encryptedResult.encryptedData!),
      CryptoUtil.bin2base64(fileNonce),
      CryptoUtil.bin2base64(encryptedResult.nonce!),
      partSize: multipartPartSizeForUpload,
      fileMd5: fileMd5,
      partMd5s: partMd5s,
    );
  }

  Future<String> putExistingMultipartFile(
    File encryptedFile,
    String localId,
    String fileHash,
    int collectionID,
    String encryptedFileName, {
    ProgressCallback? onSendProgress,
    VoidCallback? onRestart,
  }) async {
    final multipartInfo = await _db.getCachedLinks(
      localId,
      fileHash,
      collectionID,
      encryptedFileName,
    );
    await _db.updateLastAttempted(localId, fileHash, collectionID);
    _logger.info(
      "Resuming multipart upload ${multipartInfo.urls.objectKey} tracked "
      "since ${multipartInfo.createdAt} UTC: "
      "${multipartInfo.partUploadStatus?.where((done) => done).length} of "
      "${multipartInfo.urls.partsURLs.length} parts uploaded, "
      "status ${multipartInfo.status.name}",
    );

    try {
      return await _put(multipartInfo, encryptedFile, onSendProgress);
    } on DioException catch (e) {
      final statusCode = e.response?.statusCode;
      if (statusCode != 401 && statusCode != 403 && statusCode != 404) {
        rethrow;
      }
      _logger.warning(
        "Tracked multipart upload ${multipartInfo.urls.objectKey} rejected "
        "with HTTP $statusCode (sizeBytes=${multipartInfo.encFileSize} "
        "partCount=${multipartInfo.urls.partsURLs.length} "
        "resumeStatus=${multipartInfo.status.name}), restarting with the "
        "same encrypted file",
      );
      final partMd5s = multipartInfo.partMd5s;
      if (partMd5s == null) {
        throw MultiPartError("Missing part MD5s for multipart upload");
      }
      final urls = await getMultipartUploadURLs(
        count: multipartInfo.urls.partsURLs.length,
        contentLength: multipartInfo.encFileSize,
        partLength: multipartInfo.partSize!,
        partMd5s: partMd5s,
      );
      await _db.replaceMultipartUpload(multipartInfo.urls.objectKey, urls);
      onRestart?.call();
      return _put(
        MultipartInfo(
          urls: urls,
          encFileSize: multipartInfo.encFileSize,
          partSize: multipartInfo.partSize,
          fileMd5: multipartInfo.fileMd5,
          partMd5s: partMd5s,
        ),
        encryptedFile,
        onSendProgress,
      );
    }
  }

  Future<String> putMultipartFile(
    MultipartUploadURLs urls,
    File encryptedFile,
    int fileSize, {
    String? fileMd5,
    List<String>? partMd5s,
    ProgressCallback? onSendProgress,
  }) => _put(
    MultipartInfo(
      urls: urls,
      encFileSize: fileSize,
      fileMd5: fileMd5,
      partMd5s: partMd5s,
    ),
    encryptedFile,
    onSendProgress,
  );

  Future<String> _put(
    MultipartInfo info,
    File encryptedFile,
    ProgressCallback? onSendProgress,
  ) async {
    var etags = info.partETags ?? {};
    if (info.status == MultipartStatus.pending) {
      etags = await _uploadParts(
        info,
        encryptedFile,
        onSendProgress: onSendProgress,
      );
    } else {
      onSendProgress?.call(info.encFileSize, info.encFileSize);
    }
    if (info.status != MultipartStatus.completed) {
      await _completeMultipartUpload(
        info.urls.objectKey,
        etags,
        info.urls.completeURL,
      );
    }
    return info.urls.objectKey;
  }

  Future<Map<int, String>> _uploadParts(
    MultipartInfo partInfo,
    File encryptedFile, {
    ProgressCallback? onSendProgress,
  }) async {
    final partsURLs = partInfo.urls.partsURLs;
    final partUploadStatus = partInfo.partUploadStatus;
    final partsLength = partsURLs.length;
    final etags = partInfo.partETags ?? <int, String>{};
    final partMd5s = partInfo.partMd5s;
    final useUploadProxy = _shouldUseCFUploadProxy;

    int i = 0;
    final partSize = partInfo.partSize ?? multipartPartSizeForUpload;

    while (i < (partUploadStatus?.length ?? 0) &&
        (partUploadStatus?[i] ?? false)) {
      i++;
    }

    final int encFileLength = encryptedFile.lengthSync();
    if (encFileLength != partInfo.encFileSize) {
      throw MultiPartError(
        "File size mismatch. Expected ${partInfo.encFileSize} but got $encFileLength",
      );
    }
    final expectedCount = (encFileLength / partSize).ceil();
    if (partsLength != expectedCount) {
      _logger.severe(
        'Multipart parts mismatch for key ${partInfo.urls.objectKey}. '
        'Expected $expectedCount parts (encFileLength=$encFileLength, partSize=$partSize) '
        'but got $partsLength',
      );
      throw MultiPartError('multipart url count mismatch');
    }
    var completedBytes = (i * partSize).clamp(0, encFileLength).toInt();
    onSendProgress?.call(completedBytes, encFileLength);
    int count = 0;
    while (i < partsLength) {
      count++;
      final partURL = partsURLs[i];
      final isLastPart = i == partsLength - 1;
      final start = i * partSize;
      final fileSize = isLastPart ? encFileLength - start : partSize;
      _logger.info(
        "Uploading part ${i + 1} / $partsLength of size $fileSize bytes (total size $encFileLength). ObjectKey=${partInfo.urls.objectKey}",
      );
      if (kDebugMode && count > 3) {
        throw Exception(
          'Forced exception to test multipart upload retry mechanism.',
        );
      }

      final headers = {
        Headers.contentLengthHeader: fileSize,
        Headers.contentTypeHeader: "application/octet-stream",
      };

      if (partMd5s != null && i < partMd5s.length) {
        headers[useUploadProxy ? 'CONTENT-MD5' : 'Content-MD5'] = partMd5s[i];
      } else if (kDebugMode) {
        AssertionError('Part MD5s not available for part ${i + 1}');
      }
      if (useUploadProxy) {
        headers["UPLOAD-URL"] = partURL;
      }

      var partBytesSent = 0;
      try {
        late final Response<dynamic> response;
        for (var attempt = 1; ; attempt++) {
          try {
            response = await _s3Dio.put(
              useUploadProxy
                  ? "$kUploadProxyEndpoint/multipart-upload"
                  : partURL,
              data: _readPart(encryptedFile, i + 1, start, fileSize),
              options: Options(headers: headers),
              onSendProgress: (sent, _) {
                if (sent > partBytesSent) {
                  partBytesSent = sent;
                  onSendProgress?.call(
                    completedBytes + partBytesSent,
                    encFileLength,
                  );
                }
              },
            );
            break;
          } on DioException catch (error) {
            if (error.response?.statusCode != 520 ||
                attempt >= _maximumPartUploadAttempts) {
              rethrow;
            }
            _logger.info(
              "Multipart part PUT received HTTP 520; retrying immediately "
              "(${attempt + 1}/$_maximumPartUploadAttempts)",
            );
          }
        }

        final eTag = useUploadProxy
            ? _extractProxyETag(response.data)
            : response.headers.value("etag");

        if (eTag?.isEmpty ?? true) {
          throw Exception('ETAG_MISSING');
        }

        etags[i] = eTag!;

        await _db.updatePartStatus(partInfo.urls.objectKey, i, eTag);
        completedBytes += fileSize;
        onSendProgress?.call(completedBytes, encFileLength);
        i++;
      } on DioException catch (e) {
        if (e.error case final MultiPartError error) {
          Error.throwWithStackTrace(error, e.stackTrace);
        }
        if (e.response?.statusCode == 400 &&
                e.response?.data.toString().contains('BadDigest') == true ||
            e.response?.data.toString().contains('InvalidDigest') == true) {
          final recomputedMD5 = await computeMd5(
            encryptedFile.path,
            start: start,
            end: start + fileSize,
          );
          throw BadMD5DigestError(
            "Failed for part ${i + 1} ${e.response?.data}, sent: ${partMd5s?[i]}, computed: $recomputedMD5",
          );
        }
        rethrow;
      }
    }

    await _db.updateTrackUploadStatus(
      partInfo.urls.objectKey,
      MultipartStatus.uploaded,
    );

    return etags;
  }

  Stream<List<int>> _readPart(
    File file,
    int part,
    int start,
    int length,
  ) async* {
    var bytesRead = 0;
    try {
      await for (final chunk in file.openRead(start, start + length)) {
        bytesRead += chunk.length;
        yield chunk;
      }
    } on FileSystemException catch (error, stackTrace) {
      Error.throwWithStackTrace(
        MultiPartError(
          "Could not read multipart part $part after $bytesRead of $length bytes: "
          "${error.osError?.message ?? error.message}",
        ),
        stackTrace,
      );
    }
    if (bytesRead != length) {
      throw MultiPartError(
        "Multipart part $part length mismatch: expected $length bytes, read $bytesRead",
      );
    }
  }

  Future<void> _completeMultipartUpload(
    String objectKey,
    Map<int, String> partEtags,
    String completeURL,
  ) async {
    final useUploadProxy = _shouldUseCFUploadProxy;
    final body = convertJs2Xml({
      'CompleteMultipartUpload': partEtags.entries
          .map((e) => PartETag(e.key + 1, e.value))
          .toList(),
    }).replaceAll('"', '').replaceAll('&quot;', '');

    try {
      await _s3Dio.post(
        useUploadProxy
            ? "$kUploadProxyEndpoint/multipart-complete"
            : completeURL,
        data: body,
        options: Options(
          contentType: "text/xml",
          headers: useUploadProxy ? {"UPLOAD-URL": completeURL} : null,
        ),
      );
      await _db.updateTrackUploadStatus(objectKey, MultipartStatus.completed);
    } catch (e) {
      Logger("MultipartUpload").severe("upload failed for key $objectKey}", e);
      rethrow;
    }
  }

  String? _extractProxyETag(dynamic responseData) {
    if (responseData is Map) {
      final etag = responseData["etag"];
      if (etag is String) {
        return etag;
      }
    }
    return null;
  }
}
