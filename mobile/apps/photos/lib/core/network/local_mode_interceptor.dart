import 'package:dio/dio.dart';
import 'package:ente_crypto/ente_crypto.dart';
import 'package:ente_feature_flag/src/model.dart';
import 'package:nanoid/nanoid.dart';
import 'package:photos/core/configuration.dart';
import 'package:photos/core/local_mode.dart';
import 'package:photos/models/api/billing/subscription.dart';

class LocalModeNetworkException implements Exception {
  final String message;

  LocalModeNetworkException(this.message);

  @override
  String toString() => message;
}

class LocalModeInterceptor extends Interceptor {
  final String endpoint;

  LocalModeInterceptor(this.endpoint);

  bool get _isActive => isLocalOnlyDemo;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (!_isActive) {
      handler.next(options);
      return;
    }

    final uri = options.uri.toString();
    if (_isDiffOrSyncRequest(uri)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: _fakeDiffResponse(),
        ),
      );
      return;
    }

    if (_isCollectionsSinceRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: _fakeCollectionsResponse(),
        ),
      );
      return;
    }

    if (_isCollectionsCreateRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: _fakeCollectionCreateResponse(options),
        ),
      );
      return;
    }

    if (_isFeatureFlagRequest(options)) {
      final mockFlags = RemoteFlags.defaultValue.copyWith(
        enableMobMultiPart: false,
        serverApiFlag: 0,
        faceSearchEnabled: true,
      );
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: mockFlags.toMap(),
        ),
      );
      return;
    }

    if (_isUploadUrlRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: _fakeUploadUrlsResponse(options),
        ),
      );
      return;
    }

    if (_isUserDetailsRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: _fakeUserDetailsResponse(),
        ),
      );
      return;
    }

    if (_isPendingRemovalRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"actions": const <dynamic>[]},
        ),
      );
      return;
    }

    if (_isTwoFactorStatusRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": false},
        ),
      );
      return;
    }

    if (_isFileCreateRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: _fakeFileResponse(),
        ),
      );
      return;
    }

    if (_isFileUpdateRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: _fakeFileResponse(),
        ),
      );
      return;
    }

    if (_isPushTokenRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": "ok"},
        ),
      );
      return;
    }

    if (_isUserEntityKeyRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: _fakeEntityKeyResponse(options),
        ),
      );
      return;
    }

    if (_isUserEntityKeyCreateRequest(options)) {
      _cacheSubmittedEntityKey(options);
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": "ok"},
        ),
      );
      return;
    }

    if (_isUserEntityPostRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: _fakeEntityDataResponse(options),
        ),
      );
      return;
    }

    if (_isFileDataPutRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": "ok"},
        ),
      );
      return;
    }

    if (_isFileDataFetchRequest(options)) {
      final requestedIds = options.data is Map<String, dynamic>
          ? (options.data["fileIDs"] as List?)?.cast<int>() ?? const <int>[]
          : const <int>[];
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {
            "data": const <dynamic>[],
            "pendingIndexFileIDs": requestedIds,
            "errFileIDs": const <dynamic>[],
          },
        ),
      );
      return;
    }

    if (_isUserEntityPutRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: _fakeEntityDataResponse(options),
        ),
      );
      return;
    }

    if (_isCollectionsAddFilesRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": "ok"},
        ),
      );
      return;
    }

    if (_isCollectionsMoveFilesRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": "ok"},
        ),
      );
      return;
    }

    if (_isCollectionsAddFilesRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": "ok"},
        ),
      );
      return;
    }

    if (_isFileMagicMetadataRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": "ok"},
        ),
      );
      return;
    }

    if (_isCollectionsRenameRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": "ok"},
        ),
      );
      return;
    }

    if (_isCollectionsDeleteRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": "ok"},
        ),
      );
      return;
    }

    if (_isUserEntityDeleteRequest(options)) {
      handler.resolve(
        Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: {"status": "ok"},
        ),
      );
      return;
    }

    handler.reject(
      DioException(
        requestOptions: options,
        type: DioExceptionType.unknown,
        error: LocalModeNetworkException(
          "Blocked ${options.method} request to $uri (base: $endpoint) in local-only demo mode.",
        ),
      ),
    );
  }

  Map<String, dynamic> _fakeDiffResponse() {
    return {
      "diff": const <dynamic>[],
      "hasMore": false,
      "latestUpdatedAtTime": 0,
      "lastSyncedTimeStamp": 0,
    };
  }

  bool _isDiffOrSyncRequest(String url) {
    final normalized = url.toLowerCase();
    return normalized.contains("diff") || normalized.contains("sync");
  }

  bool _isCollectionsSinceRequest(RequestOptions options) {
    final path = options.uri.path.toLowerCase();
    if (!path.contains("/collections/v2")) return false;
    return options.uri.queryParameters.containsKey("sinceTime");
  }

  bool _isCollectionsCreateRequest(RequestOptions options) {
    return options.method.toUpperCase() == "POST" &&
        options.uri.path == "/collections";
  }

  bool _isFeatureFlagRequest(RequestOptions options) {
    return options.method.toUpperCase() == "GET" &&
        options.uri.path == "/remote-store/feature-flags";
  }

  bool _isUploadUrlRequest(RequestOptions options) {
    return options.method.toUpperCase() == "GET" &&
        options.uri.path == "/files/upload-urls";
  }

  bool _isFileCreateRequest(RequestOptions options) {
    return options.method.toUpperCase() == "POST" &&
        options.uri.path == "/files";
  }

  bool _isFileUpdateRequest(RequestOptions options) {
    return options.method.toUpperCase() == "PUT" &&
        options.uri.path == "/files/update";
  }

  bool _isPushTokenRequest(RequestOptions options) {
    final methodIsPost = options.method.toUpperCase() == "POST";
    final path = options.uri.path;
    return methodIsPost && (path == "/push/token" || path == "/push/tokem");
  }

  bool _isUserEntityPostRequest(RequestOptions options) {
    return options.method.toUpperCase() == "POST" &&
        options.uri.path == "/user-entity/entity";
  }

  bool _isUserEntityPutRequest(RequestOptions options) {
    return options.method.toUpperCase() == "PUT" &&
        options.uri.path == "/user-entity/entity";
  }

  bool _isCollectionsAddFilesRequest(RequestOptions options) {
    return options.method.toUpperCase() == "POST" &&
        options.uri.path == "/collections/add-files";
  }

  bool _isCollectionsMoveFilesRequest(RequestOptions options) {
    return options.method.toUpperCase() == "POST" &&
        options.uri.path == "/collections/move-files";
  }

  bool _isFileMagicMetadataRequest(RequestOptions options) {
    final path = options.uri.path;
    final method = options.method.toUpperCase();
    if (method != "PUT") return false;
    return path == "/files/magic-metadata" ||
        path == "/files/public-magic-metadata" ||
        path == "/collections/magic-metadata" ||
        path == "/collections/public-magic-metadata" ||
        path == "/collections/sharee-magic-metadata";
  }

  bool _isFileDataPutRequest(RequestOptions options) {
    return options.method.toUpperCase() == "PUT" &&
        options.uri.path == "/files/data";
  }

  bool _isUserEntityKeyRequest(RequestOptions options) {
    return options.method.toUpperCase() == "GET" &&
        options.uri.path == "/user-entity/key" &&
        (options.uri.queryParameters["type"]?.isNotEmpty ?? false);
  }

  bool _isUserEntityKeyCreateRequest(RequestOptions options) {
    return options.method.toUpperCase() == "POST" &&
        options.uri.path == "/user-entity/key";
  }

  bool _isFileDataFetchRequest(RequestOptions options) {
    return options.method.toUpperCase() == "POST" &&
        options.uri.path == "/files/data/fetch";
  }

  bool _isUserDetailsRequest(RequestOptions options) {
    return options.method.toUpperCase() == "GET" &&
        options.uri.path == "/users/details/v2";
  }

  bool _isPendingRemovalRequest(RequestOptions options) {
    return options.method.toUpperCase() == "GET" &&
        options.uri.path == "/collection-actions/pending-remove/";
  }

  bool _isTwoFactorStatusRequest(RequestOptions options) {
    return options.method.toUpperCase() == "GET" &&
        options.uri.path == "/users/two-factor/status";
  }

  bool _isCollectionsRenameRequest(RequestOptions options) {
    return options.method.toUpperCase() == "POST" &&
        options.uri.path == "/collections/rename";
  }

  bool _isCollectionsDeleteRequest(RequestOptions options) {
    return options.method.toUpperCase() == "DELETE" &&
        options.uri.path.startsWith("/collections/v3/");
  }

  bool _isUserEntityDeleteRequest(RequestOptions options) {
    return options.method.toUpperCase() == "DELETE" &&
        options.uri.path == "/user-entity/entity";
  }

  Map<String, dynamic> _fakeFileResponse() {
    final nowMicros = DateTime.now().microsecondsSinceEpoch;
    final ownerID = Configuration.instance.getUserID() ?? 1;
    return {
      "id": nowMicros,
      "updationTime": nowMicros,
      "ownerID": ownerID,
    };
  }

  Map<String, dynamic> _fakeCollectionsResponse() {
    return {
      "collections": const <dynamic>[],
    };
  }

  Map<String, dynamic> _fakeCollectionCreateResponse(RequestOptions options) {
    final now = DateTime.now().millisecondsSinceEpoch;
    final body = options.data is Map<String, dynamic>
        ? options.data as Map<String, dynamic>
        : <String, dynamic>{};
    final attributes = body["attributes"] is Map<String, dynamic>
        ? Map<String, dynamic>.from(body["attributes"] as Map)
        : <String, dynamic>{};
    attributes.putIfAbsent("version", () => 0);
    final ownerID = Configuration.instance.getUserID() ?? 1;
    return {
      "collection": {
        "id": now,
        "owner": {
          "id": ownerID,
          "email": "demo@localhost",
          "name": "Demo",
          "role": "OWNER",
        },
        "encryptedKey": body["encryptedKey"] ?? "",
        "keyDecryptionNonce": body["keyDecryptionNonce"] ?? "",
        "name": null,
        "encryptedName": body["encryptedName"] ?? "",
        "nameDecryptionNonce": body["nameDecryptionNonce"] ?? "",
        "type": body["type"] ?? "album",
        "attributes": attributes,
        "sharees": const <dynamic>[],
        "publicURLs": const <dynamic>[],
        "updationTime": now,
        "isDeleted": false,
        if (body["magicMetadata"] != null)
          "magicMetadata": body["magicMetadata"],
        if (body["pubMagicMetadata"] != null)
          "pubMagicMetadata": body["pubMagicMetadata"],
        if (body["sharedMagicMetadata"] != null)
          "sharedMagicMetadata": body["sharedMagicMetadata"],
      },
    };
  }

  Map<String, dynamic> _fakeUploadUrlsResponse(RequestOptions options) {
    final count = int.tryParse(options.uri.queryParameters["count"] ?? '') ?? 0;
    final now = DateTime.now().millisecondsSinceEpoch;
    final urls = List.generate(
      count,
      (index) => {
        "url": "https://localhost/upload/$now/$index",
        "objectKey": "local-demo-object-$now-$index",
      },
    );
    return {"urls": urls};
  }

  Map<String, dynamic> _fakeUserDetailsResponse() {
    final futureMicros =
        DateTime.now().add(const Duration(days: 365)).microsecondsSinceEpoch;
    return {
      "email": "demo@localhost",
      "usage": 0,
      "fileCount": 0,
      "storageBonus": 0,
      "sharedCollectionsCount": 0,
      "subscription": {
        "productID": freeProductID,
        "storage": 107374182400, // 100 GB
        "originalTransactionID": "local-demo",
        "paymentProvider": stripe,
        "expiryTime": futureMicros,
        "price": "0",
        "period": "year",
        "attributes": {
          "isCancelled": false,
          "customerID": "local-demo-customer",
        },
      },
      "familyData": {
        "members": const <dynamic>[],
        "storage": 107374182400,
        "expiryTime": futureMicros,
      },
      "profileData": {
        "canDisableEmailMFA": true,
        "isEmailMFAEnabled": false,
        "isTwoFactorEnabled": false,
      },
      "bonusData": {
        "storageBonuses": const <dynamic>[],
      },
    };
  }

  Map<String, dynamic> _fakeEntityKeyResponse(RequestOptions options) {
    final type = options.uri.queryParameters["type"] ?? "unknown";
    if (!_demoEntityKeyCache.containsKey(type)) {
      final pair = _generateDemoEntityKeyPair();
      _demoEntityKeyCache[type] = pair.$1;
      _demoEntityHeaderCache[type] = pair.$2;
    }
    return {
      "type": type,
      "encryptedKey": _demoEntityKeyCache[type],
      "header": _demoEntityHeaderCache[type],
    };
  }

  static final Map<String, String> _demoEntityKeyCache = {};
  static final Map<String, String> _demoEntityHeaderCache = {};

  (String, String) _generateDemoEntityKeyPair() {
    final keyBytes = CryptoUtil.generateKey();
    final encrypted =
        CryptoUtil.encryptSync(keyBytes, Configuration.instance.getKey()!);
    return (
      CryptoUtil.bin2base64(encrypted.encryptedData!),
      CryptoUtil.bin2base64(encrypted.nonce!)
    );
  }

  Map<String, dynamic> _fakeEntityDataResponse(RequestOptions options) {
    final data = options.data is Map<String, dynamic>
        ? options.data as Map<String, dynamic>
        : <String, dynamic>{};
    final now = DateTime.now().millisecondsSinceEpoch;
    return {
      "id": data["id"] ?? nanoid(),
      "type": data["type"],
      "encryptedData": data["encryptedData"],
      "header": data["header"],
      "userID": Configuration.instance.getUserID(),
      "updatedAt": now,
      "createdAt": now,
      "isDeleted": false,
    };
  }

  void _cacheSubmittedEntityKey(RequestOptions options) {
    final data = options.data is Map<String, dynamic>
        ? options.data as Map<String, dynamic>
        : <String, dynamic>{};
    final type = data["type"]?.toString();
    if (type == null) return;
    final encryptedKey = data["encryptedKey"]?.toString();
    final header = data["header"]?.toString();
    if (encryptedKey != null && encryptedKey.isNotEmpty) {
      _demoEntityKeyCache[type] = encryptedKey;
    }
    if (header != null && header.isNotEmpty) {
      _demoEntityHeaderCache[type] = header;
    }
  }
}
