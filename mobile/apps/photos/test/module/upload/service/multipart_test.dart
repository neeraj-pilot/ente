import "dart:io";

import "package:dio/dio.dart";
import "package:ente_feature_flag/ente_feature_flag.dart";
import "package:flutter_test/flutter_test.dart";
import "package:path_provider_platform_interface/path_provider_platform_interface.dart";
import "package:photos/core/errors.dart";
import "package:photos/db/upload_locks_db.dart";
import "package:photos/gateways/files/file_upload_gateway.dart";
import "package:photos/module/upload/model/multipart.dart";
import "package:photos/module/upload/service/multipart.dart";

const _localID = "local";
const _fileHash = "hash";
const _collectionID = 1;
const _encryptedFileName = "upload_file_test_file.encrypted";
const _partSize = 4;
const _payload = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
const _partMd5s = ["md5-1", "md5-2", "md5-3"];

void main() {
  final db = UploadLocksDB.instance;
  late Directory tempDir;
  late File encryptedFile;
  late PathProviderPlatform previousPathProvider;
  late _FakeDio dio;
  late _FakeGateway gateway;
  late MultiPartUploader uploader;

  setUpAll(() async {
    previousPathProvider = PathProviderPlatform.instance;
    tempDir = await Directory.systemTemp.createTemp("multipart_test_");
    PathProviderPlatform.instance = _FakePathProvider(tempDir.path);
    encryptedFile = File("${tempDir.path}/$_encryptedFileName")
      ..writeAsBytesSync(_payload);
  });

  tearDownAll(() async {
    await (await db.database).close();
    PathProviderPlatform.instance = previousPathProvider;
    await tempDir.delete(recursive: true);
  });

  setUp(() async {
    await db.clearTable();
    dio = _FakeDio();
    gateway = _FakeGateway();
    uploader = MultiPartUploader(dio, db, _FakeFlagService(), gateway);
    await db.createTrackUploadsEntry(
      _localID,
      _fileHash,
      _collectionID,
      _urls("stale"),
      _encryptedFileName,
      _payload.length,
      "fileKey",
      "fileNonce",
      "keyNonce",
      partSize: _partSize,
      partMd5s: _partMd5s,
    );
    await db.updatePartStatus("stale-key", 0, "stale-etag-1");
  });

  // A null marks a restart.
  late List<int?> progress;

  Future<String> resume() {
    progress = [];
    return uploader.putExistingMultipartFile(
      encryptedFile,
      _localID,
      _fileHash,
      _collectionID,
      _encryptedFileName,
      onSendProgress: (sent, _) => progress.add(sent),
      onRestart: () => progress.add(null),
    );
  }

  Future<MultipartInfo> tracked() =>
      db.getCachedLinks(_localID, _fileHash, _collectionID, _encryptedFileName);

  for (final (statusCode, rejectedURL) in [
    (404, "stale-part-2"),
    (403, "stale-part-2"),
    (401, "stale-part-2"),
    (404, "stale-complete"),
  ]) {
    test("restarts with the same encrypted file when $rejectedURL "
        "returns $statusCode", () async {
      dio.failures[rejectedURL] = statusCode;

      expect(await resume(), "fresh-key");

      expect(progress.where((sent) => sent == null), hasLength(1));
      expect(progress.skipWhile((sent) => sent != null).take(2), [null, 0]);
      final urlRequest = gateway.requests.single;
      expect(urlRequest.contentLength, _payload.length);
      expect(urlRequest.partLength, _partSize);
      expect(urlRequest.partMd5s, _partMd5s);
      final freshRequests = dio.requests
          .skipWhile((request) => request.path.startsWith("stale"))
          .toList();
      expect(freshRequests.map((request) => request.path), [
        "fresh-part-1",
        "fresh-part-2",
        "fresh-part-3",
        "fresh-complete",
      ]);
      expect(
        freshRequests.take(3).expand((request) => request.body).toList(),
        _payload,
      );
      expect(
        freshRequests.take(3).map((request) => request.headers["Content-MD5"]),
        _partMd5s,
      );

      final info = await tracked();
      expect(info.urls.objectKey, "fresh-key");
      expect(info.urls.partsURLs, _urls("fresh").partsURLs);
      expect(info.status, MultipartStatus.completed);
      expect(info.partUploadStatus, [true, true, true]);
      expect(info.partMd5s, _partMd5s);
    });
  }

  test("keeps the tracked upload on failures that can be resumed", () async {
    dio.failures["stale-part-3"] = 500;

    await expectLater(resume(), throwsA(isA<DioException>()));

    expect(gateway.requests, isEmpty);
    expect(progress, isNot(contains(null)));
    final info = await tracked();
    expect(info.urls.objectKey, "stale-key");
    expect(info.status, MultipartStatus.pending);
    expect(info.partUploadStatus, [true, true, false]);
  });

  test("discards the upload only when it cannot be resumed", () {
    bool discards(Object error, {bool duringCommit = false}) =>
        shouldDiscardMultipartUpload(error, duringCommit: duringCommit);

    for (final error in <Object>[
      MultiPartError("url count mismatch"),
      MultiPartFileMissingError("encrypted file missing"),
      BadMD5DigestError("part 2"),
    ]) {
      expect(discards(error), isTrue, reason: "$error");
    }
    for (final statusCode in [null, 400, 429, 503]) {
      final error = _dioError("/files", statusCode);
      expect(discards(error, duringCommit: true), isTrue, reason: "$error");
      expect(discards(error), isFalse, reason: "$error");
    }
    expect(discards(Exception("ETAG_MISSING"), duringCommit: true), isFalse);
  });

  test("discards a rejected upload that has no part MD5s", () async {
    await db.clearTable();
    await db.createTrackUploadsEntry(
      _localID,
      _fileHash,
      _collectionID,
      _urls("stale"),
      _encryptedFileName,
      _payload.length,
      "fileKey",
      "fileNonce",
      "keyNonce",
      partSize: _partSize,
    );
    dio.failures["stale-part-1"] = 403;

    await expectLater(resume(), throwsA(isA<MultiPartError>()));

    expect(gateway.requests, isEmpty);
  });

  test("discards an upload whose encrypted file changed size", () async {
    await db.clearTable();
    await db.createTrackUploadsEntry(
      _localID,
      _fileHash,
      _collectionID,
      _urls("stale"),
      _encryptedFileName,
      _payload.length + 1,
      "fileKey",
      "fileNonce",
      "keyNonce",
      partSize: _partSize,
      partMd5s: _partMd5s,
    );

    await expectLater(resume(), throwsA(isA<MultiPartError>()));

    expect(dio.requests, isEmpty);
  });

  test("leaves the restarted upload resumable when it fails too", () async {
    dio.failures["stale-part-2"] = 404;
    dio.failures["fresh-part-2"] = 404;

    await expectLater(resume(), throwsA(isA<DioException>()));

    expect(gateway.requests, hasLength(1));
    final info = await tracked();
    expect(info.urls.objectKey, "fresh-key");
    expect(info.partUploadStatus, [true, false, false]);
  });
}

DioException _dioError(String path, [int? statusCode]) {
  final requestOptions = RequestOptions(path: path);
  return DioException(
    requestOptions: requestOptions,
    response: statusCode == null
        ? null
        : Response(requestOptions: requestOptions, statusCode: statusCode),
  );
}

MultipartUploadURLs _urls(String name) => MultipartUploadURLs(
  objectKey: "$name-key",
  partsURLs: ["$name-part-1", "$name-part-2", "$name-part-3"],
  completeURL: "$name-complete",
);

class _FakeDio extends Fake implements Dio {
  final failures = <String, int>{};
  final requests =
      <({String path, List<int> body, Map<String, dynamic> headers})>[];

  @override
  Future<Response<T>> put<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    final body = await (data! as Stream<List<int>>).fold<List<int>>(
      [],
      (bytes, chunk) => bytes..addAll(chunk),
    );
    return _respond(path, body, options);
  }

  @override
  Future<Response<T>> post<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async => _respond(path, const [], options);

  Response<T> _respond<T>(String path, List<int> body, Options? options) {
    requests.add((
      path: path,
      body: body,
      headers: options?.headers ?? const {},
    ));
    final statusCode = failures[path];
    if (statusCode != null) {
      throw _dioError(path, statusCode);
    }
    return Response<T>(
      requestOptions: RequestOptions(path: path),
      headers: Headers.fromMap({
        "etag": ["etag-$path"],
      }),
    );
  }
}

class _FakeGateway extends Fake implements FileUploadGateway {
  final requests =
      <({int contentLength, int partLength, List<String> partMd5s})>[];

  @override
  Future<MultipartUploadURLs> getMultipartUploadUrl({
    required int contentLength,
    required int partLength,
    required List<String> partMd5s,
  }) async {
    requests.add((
      contentLength: contentLength,
      partLength: partLength,
      partMd5s: partMd5s,
    ));
    return _urls("fresh");
  }
}

class _FakeFlagService extends Fake implements FlagService {
  @override
  bool get disableCFWorker => true;
}

class _FakePathProvider extends PathProviderPlatform {
  _FakePathProvider(this.path);

  final String path;

  @override
  Future<String?> getApplicationDocumentsPath() async => path;
}
