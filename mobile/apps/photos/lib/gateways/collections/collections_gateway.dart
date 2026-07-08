import "package:dio/dio.dart";
import "package:photos/core/exceptions.dart";
import "package:photos/gateways/collections/models/create_request.dart";
import "package:photos/gateways/collections/models/metadata.dart";

class MalformedCollectionsResponseException
    implements Exception, LocallyHandledError {
  final String endpoint;
  final String field;
  final String expected;
  final String actualType;

  MalformedCollectionsResponseException({
    required this.endpoint,
    required this.field,
    required this.expected,
    required Object? actual,
  }) : actualType = actual == null ? "null" : actual.runtimeType.toString();

  @override
  String toString() {
    return "MalformedCollectionsResponseException: expected $expected at "
        "$endpoint.$field, got $actualType";
  }
}

/// Gateway for collection CRUD and metadata API endpoints.
///
/// Handles collection creation, retrieval, deletion, renaming, and
/// metadata updates for the Ente Photos API.
class CollectionsGateway {
  final Dio _enteDio;

  CollectionsGateway(this._enteDio);

  /// Creates a new collection.
  ///
  /// [createRequest] - The request containing collection details.
  ///
  /// Returns the raw collection data from the API response.
  Future<Map<String, dynamic>> createCollection(
    CreateRequest createRequest,
  ) async {
    final response = await _enteDio.post(
      "/collections",
      data: createRequest.toJson(),
    );
    final data = _responseMap(response, "/collections");
    return _mapField(data, "collection", "/collections");
  }

  /// Gets a collection by its ID.
  ///
  /// [collectionID] - The ID of the collection to retrieve.
  ///
  /// Returns the raw collection data from the API response.
  Future<Map<String, dynamic>> getCollection(int collectionID) async {
    final response = await _enteDio.get("/collections/$collectionID");
    final data = _responseMap(response, "/collections/$collectionID");
    return _mapField(data, "collection", "/collections/$collectionID");
  }

  /// Deletes a collection.
  ///
  /// [collectionID] - The ID of the collection to delete.
  /// [keepFiles] - If true, files are kept; if false, files are moved to trash.
  Future<void> deleteCollection({
    required int collectionID,
    required bool keepFiles,
  }) async {
    await _enteDio.delete(
      "/collections/v3/$collectionID?keepFiles=$keepFiles&collectionID=$collectionID",
    );
  }

  /// Renames a collection.
  ///
  /// [collectionID] - The ID of the collection to rename.
  /// [encryptedName] - The new name, encrypted with the collection key.
  /// [nameDecryptionNonce] - The nonce used for encryption.
  Future<void> renameCollection({
    required int collectionID,
    required String encryptedName,
    required String nameDecryptionNonce,
  }) async {
    await _enteDio.post(
      "/collections/rename",
      data: {
        "collectionID": collectionID,
        "encryptedName": encryptedName,
        "nameDecryptionNonce": nameDecryptionNonce,
      },
    );
  }

  /// Leaves a shared collection.
  ///
  /// [collectionID] - The ID of the collection to leave.
  Future<void> leaveCollection(int collectionID) async {
    await _enteDio.post("/collections/leave/$collectionID");
  }

  /// Gets the diff of files in a collection since a given time.
  ///
  /// [collectionID] - The ID of the collection.
  /// [sinceTime] - The timestamp to get changes since.
  ///
  /// Returns the raw response data containing the diff.
  Future<Map<String, dynamic>> getDiff({
    required int collectionID,
    required int sinceTime,
  }) async {
    final response = await _enteDio.get(
      "/collections/v2/diff",
      queryParameters: {"collectionID": collectionID, "sinceTime": sinceTime},
    );
    return _responseMap(response, "/collections/v2/diff");
  }

  /// Updates the private magic metadata of a collection.
  ///
  /// [request] - The request containing the collection ID and metadata.
  Future<void> updateMagicMetadata(UpdateMagicMetadataRequest request) async {
    await _enteDio.put("/collections/magic-metadata", data: request.toJson());
  }

  /// Updates the public magic metadata of a collection.
  ///
  /// [request] - The request containing the collection ID and metadata.
  Future<void> updatePublicMagicMetadata(
    UpdateMagicMetadataRequest request,
  ) async {
    await _enteDio.put(
      "/collections/public-magic-metadata",
      data: request.toJson(),
    );
  }

  /// Updates the sharee magic metadata of a collection.
  ///
  /// [request] - The request containing the collection ID and metadata.
  Future<void> updateShareeMagicMetadata(
    UpdateMagicMetadataRequest request,
  ) async {
    await _enteDio.put(
      "/collections/sharee-magic-metadata",
      data: request.toJson(),
    );
  }

  /// Joins a collection via a public link.
  ///
  /// [collectionID] - The ID of the collection to join.
  /// [encryptedKey] - The collection key encrypted for the joining user.
  /// [headers] - The public collection authentication headers.
  Future<void> joinViaLink({
    required int collectionID,
    required String encryptedKey,
    required Map<String, String> headers,
  }) async {
    await _enteDio.post(
      "/collections/join-link",
      data: {"collectionID": collectionID, "encryptedKey": encryptedKey},
      options: Options(headers: headers),
    );
  }

  /// Gets all collections for the current user.
  ///
  /// [sinceTime] - The timestamp to get collections updated since.
  /// [source] - The source of the request (e.g., "fg" for foreground).
  ///
  /// Returns the raw response data containing the collections list.
  Future<Map<String, dynamic>> getAll({
    required int sinceTime,
    required String source,
  }) async {
    final response = await _enteDio.get(
      "/collections/v2",
      queryParameters: {"sinceTime": sinceTime, "source": source},
    );
    return _responseMap(response, "/collections/v2");
  }

  /// Fetches pending removal actions for collections.
  ///
  /// Returns the raw response data containing pending actions.
  Future<Map<String, dynamic>> fetchPendingRemovalActions() async {
    final response = await _enteDio.get("/collection-actions/pending-remove");
    return _responseMap(response, "/collection-actions/pending-remove");
  }

  /// Fetches delete suggestion actions for collections.
  ///
  /// Returns the raw response data containing delete suggestion actions.
  Future<Map<String, dynamic>> fetchDeleteSuggestions() async {
    final response = await _enteDio.get(
      "/collection-actions/delete-suggestions",
    );
    return _responseMap(response, "/collection-actions/delete-suggestions");
  }

  /// Rejects delete suggestions for specified files.
  ///
  /// [fileIDs] - The list of file IDs to reject delete suggestions for.
  Future<void> rejectDeleteSuggestions(List<int> fileIDs) async {
    await _enteDio.post(
      "/collection-actions/reject-delete-suggestions",
      data: {"fileIDs": fileIDs},
    );
  }

  Map<String, dynamic> _responseMap(
    Response<dynamic> response,
    String endpoint,
  ) {
    return _mapValue(response.data, endpoint, "response");
  }

  Map<String, dynamic> _mapField(
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
          throw MalformedCollectionsResponseException(
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
    throw MalformedCollectionsResponseException(
      endpoint: endpoint,
      field: field,
      expected: "JSON object",
      actual: value,
    );
  }
}
