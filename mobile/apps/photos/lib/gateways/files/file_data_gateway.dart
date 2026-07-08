import "package:dio/dio.dart";
import "package:photos/core/exceptions.dart";

class MalformedFileDataResponseException
    implements Exception, LocallyHandledError {
  final String endpoint;
  final String field;
  final String expected;
  final String actualType;

  MalformedFileDataResponseException({
    required this.endpoint,
    required this.field,
    required this.expected,
    required Object? actual,
  }) : actualType = actual == null ? "null" : actual.runtimeType.toString();

  @override
  String toString() {
    return "MalformedFileDataResponseException: expected $expected at "
        "$endpoint.$field, got $actualType";
  }
}

/// Gateway for file data API endpoints.
///
/// Handles file data operations including ML data storage/retrieval,
/// video preview data, and public collection file data.
class FileDataGateway {
  final Dio _enteDio;

  FileDataGateway(this._enteDio);

  /// Stores file data (e.g., ML embeddings) for a file.
  ///
  /// [fileID] - The uploaded file ID.
  /// [type] - The type of data being stored.
  /// [encryptedData] - The encrypted data payload.
  /// [decryptionHeader] - Header required for decryption.
  Future<void> putFileData({
    required int fileID,
    required String type,
    required String encryptedData,
    required String decryptionHeader,
  }) async {
    await _enteDio.put(
      "/files/data",
      data: {
        "fileID": fileID,
        "type": type,
        "encryptedData": encryptedData,
        "decryptionHeader": decryptionHeader,
      },
    );
  }

  /// Fetches file data for the given file IDs.
  ///
  /// [fileIDs] - List of file IDs to fetch data for.
  /// [type] - The type of data to fetch.
  ///
  /// Returns raw response data containing 'data', 'pendingIndexFileIDs',
  /// and 'errFileIDs'.
  Future<Map<String, dynamic>> fetchFileData({
    required List<int> fileIDs,
    required String type,
  }) async {
    final response = await _enteDio.post(
      "/files/data/fetch",
      data: {"fileIDs": fileIDs, "type": type},
    );
    return _responseMap(response, "/files/data/fetch");
  }

  /// Gets the file data status diff since the given timestamp.
  ///
  /// [lastUpdatedAt] - Timestamp to fetch changes from.
  ///
  /// Returns the diff data containing updated file data statuses.
  Future<Map<String, dynamic>> getStatusDiff({
    required int lastUpdatedAt,
  }) async {
    final response = await _enteDio.post(
      "/files/data/status-diff",
      data: {"lastUpdatedAt": lastUpdatedAt},
    );
    return _responseMap(response, "/files/data/status-diff");
  }

  /// Stores video preview data for a file.
  ///
  /// [fileID] - The uploaded file ID.
  /// [objectID] - The object ID of the uploaded preview.
  /// [objectSize] - Size of the preview object.
  /// [playlist] - Encrypted HLS playlist data.
  /// [playlistHeader] - Header required for playlist decryption.
  /// [cancelToken] - Optional token to cancel the request.
  Future<void> putVideoData({
    required int fileID,
    required String objectID,
    required int objectSize,
    required String playlist,
    required String playlistHeader,
    CancelToken? cancelToken,
  }) async {
    await _enteDio.put(
      "/files/video-data",
      data: {
        "fileID": fileID,
        "objectID": objectID,
        "objectSize": objectSize,
        "playlist": playlist,
        "playlistHeader": playlistHeader,
      },
      cancelToken: cancelToken,
    );
  }

  /// Gets an upload URL for file preview data.
  ///
  /// [fileID] - The uploaded file ID.
  /// [type] - The type of preview (e.g., "vid_preview", "img_preview").
  /// [cancelToken] - Optional token to cancel the request.
  ///
  /// Returns the upload URL and object ID.
  Future<({String url, String objectID})> getPreviewUploadUrl({
    required int fileID,
    required String type,
    CancelToken? cancelToken,
  }) async {
    final response = await _enteDio.get(
      "/files/data/preview-upload-url",
      queryParameters: {"fileID": fileID, "type": type},
      cancelToken: cancelToken,
    );
    final data = _responseMap(response, "/files/data/preview-upload-url");
    return (
      url: _stringField(data, "url", "/files/data/preview-upload-url"),
      objectID: _stringField(
        data,
        "objectID",
        "/files/data/preview-upload-url",
      ),
    );
  }

  /// Gets the preview URL for a file.
  ///
  /// [fileID] - The uploaded file ID.
  /// [type] - The type of preview (e.g., "vid_preview", "img_preview").
  ///
  /// Returns the preview URL.
  Future<String> getPreview({required int fileID, required String type}) async {
    final response = await _enteDio.get(
      "/files/data/preview",
      queryParameters: {"fileID": fileID, "type": type},
    );
    final data = _responseMap(response, "/files/data/preview");
    return _stringField(data, "url", "/files/data/preview");
  }

  /// Fetches file data for a single file (for video preview playlist).
  ///
  /// [fileID] - The uploaded file ID.
  /// [type] - The type of data to fetch.
  ///
  /// Returns the encrypted data and decryption header.
  Future<({String encryptedData, String decryptionHeader})>
  fetchSingleFileData({required int fileID, required String type}) async {
    final response = await _enteDio.get(
      "/files/data/fetch/",
      queryParameters: {"fileID": fileID, "type": type},
    );
    final data = _responseMap(response, "/files/data/fetch/");
    final fileData = _nestedMap(data, "data", "/files/data/fetch/");
    return (
      encryptedData: _stringField(
        fileData,
        "encryptedData",
        "/files/data/fetch/",
      ),
      decryptionHeader: _stringField(
        fileData,
        "decryptionHeader",
        "/files/data/fetch/",
      ),
    );
  }

  /// Fetches public collection file data.
  ///
  /// [baseUrl] - The base API URL.
  /// [fileID] - The uploaded file ID.
  /// [type] - The type of data to fetch.
  /// [headers] - Headers for public collection authentication.
  /// [nonEnteDio] - Dio instance for non-authenticated requests.
  ///
  /// Returns the encrypted data and decryption header.
  Future<({String encryptedData, String decryptionHeader})>
  fetchPublicFileData({
    required String baseUrl,
    required int fileID,
    required String type,
    required Map<String, dynamic> headers,
    required Dio nonEnteDio,
  }) async {
    final response = await nonEnteDio.get(
      "$baseUrl/public-collection/files/data/fetch/",
      queryParameters: {"fileID": fileID, "type": type},
      options: Options(headers: headers),
    );
    final data = _responseMap(response, "/public-collection/files/data/fetch/");
    final fileData = _nestedMap(
      data,
      "data",
      "/public-collection/files/data/fetch/",
    );
    return (
      encryptedData: _stringField(
        fileData,
        "encryptedData",
        "/public-collection/files/data/fetch/",
      ),
      decryptionHeader: _stringField(
        fileData,
        "decryptionHeader",
        "/public-collection/files/data/fetch/",
      ),
    );
  }

  /// Gets the public preview URL for a file.
  ///
  /// [baseUrl] - The base API URL.
  /// [fileID] - The uploaded file ID.
  /// [type] - The type of preview (e.g., "vid_preview", "img_preview").
  /// [headers] - Headers for public collection authentication.
  /// [nonEnteDio] - Dio instance for non-authenticated requests.
  ///
  /// Returns the preview URL.
  Future<String> getPublicPreview({
    required String baseUrl,
    required int fileID,
    required String type,
    required Map<String, dynamic> headers,
    required Dio nonEnteDio,
  }) async {
    final response = await nonEnteDio.get(
      "$baseUrl/public-collection/files/data/preview",
      queryParameters: {"fileID": fileID, "type": type},
      options: Options(headers: headers),
    );
    final data = _responseMap(
      response,
      "/public-collection/files/data/preview",
    );
    return _stringField(data, "url", "/public-collection/files/data/preview");
  }

  Map<String, dynamic> _responseMap(
    Response<dynamic> response,
    String endpoint,
  ) {
    return _mapValue(response.data, endpoint, "response");
  }

  Map<String, dynamic> _nestedMap(
    Map<String, dynamic> data,
    String key,
    String endpoint,
  ) {
    return _mapValue(data[key], endpoint, key);
  }

  Map<String, dynamic> _mapValue(Object? value, String endpoint, String field) {
    if (value is Map<String, dynamic>) {
      return value;
    }
    if (value is Map) {
      final result = <String, dynamic>{};
      for (final entry in value.entries) {
        final key = entry.key;
        if (key is! String) {
          throw MalformedFileDataResponseException(
            endpoint: endpoint,
            field: field,
            expected: "JSON object with string keys",
            actual: value,
          );
        }
        result[key] = entry.value;
      }
      return result;
    }
    throw MalformedFileDataResponseException(
      endpoint: endpoint,
      field: field,
      expected: "JSON object",
      actual: value,
    );
  }

  String _stringField(Map<String, dynamic> data, String key, String endpoint) {
    final value = data[key];
    if (value is String) {
      return value;
    }
    throw MalformedFileDataResponseException(
      endpoint: endpoint,
      field: key,
      expected: "string",
      actual: value,
    );
  }
}
