import 'package:flutter_test/flutter_test.dart';
import 'package:photos/core/constants.dart';
import 'package:photos/models/file/file.dart';
import 'package:photos/models/file/file_type.dart';
import 'package:photos/module/upload/model/media_upload_data.dart';
import 'package:photos/module/upload/service/existing_upload_resolver.dart';

void main() {
  test(
    'continues without querying when the input is already uploaded',
    () async {
      final actions = _FakeExistingUploadActions();
      final resolver = ExistingUploadResolver(actions: actions);

      final result = await _resolve(
        resolver,
        _file(localID: 'local', uploadedFileID: 1),
      );

      expect(result, isA<UploadRequired>());
      expect(actions.findCalls, 0);
    },
  );

  test('continues when hash matches belong to another local file', () async {
    final actions = _FakeExistingUploadActions()
      ..matches = [
        _file(localID: 'other-local', uploadedFileID: 1, collectionID: 10),
      ];
    final resolver = ExistingUploadResolver(actions: actions);

    final result = await _resolve(resolver, _file(localID: 'local'));

    expect(result, isA<UploadRequired>());
    expect(actions.deletedGeneratedIDs, isEmpty);
    expect(actions.notifications, isEmpty);
  });

  test('resolves an existing file in the destination collection', () async {
    final existing = _file(
      localID: 'local',
      uploadedFileID: 1,
      collectionID: 10,
    );
    final actions = _FakeExistingUploadActions()..matches = [existing];
    final resolver = ExistingUploadResolver(actions: actions);

    final result = await _resolve(
      resolver,
      _file(localID: 'local', generatedID: 2),
    );

    expect(result, isA<ResolvedExistingUpload>());
    final resolved = result as ResolvedExistingUpload;
    expect(resolved.file, same(existing));
    expect(resolved.match, ExistingUploadMatch.sameLocalSameCollection);
    expect(actions.deletedGeneratedIDs, [2]);
    expect(actions.notifications.single.$2, 'sameLocalSameCollection');
  });

  test('adopts a missing local ID only after persistence succeeds', () async {
    final existing = _file(uploadedFileID: 1, collectionID: 10);
    final actions = _FakeExistingUploadActions()..matches = [existing];
    final resolver = ExistingUploadResolver(actions: actions);

    final result = await _resolve(
      resolver,
      _file(localID: 'local', generatedID: 2),
    );

    final resolved = result as ResolvedExistingUpload;
    expect(resolved.file, same(existing));
    expect(resolved.match, ExistingUploadMatch.adoptedMissingLocalID);
    expect(existing.localID, 'local');
    expect(actions.assignedLocalIDs, [(1, 'local')]);
    expect(actions.deletedGeneratedIDs, [2]);
    expect(actions.notifications.single.$2, 'fileMissingLocal');
  });

  test('prefers a destination match over a missing local ID', () async {
    final destinationMatch = _file(
      localID: 'local',
      uploadedFileID: 1,
      collectionID: 10,
    );
    final missingLocalID = _file(uploadedFileID: 2, collectionID: 20);
    final actions = _FakeExistingUploadActions()
      ..matches = [missingLocalID, destinationMatch];
    final resolver = ExistingUploadResolver(actions: actions);

    final result = await _resolve(
      resolver,
      _file(localID: 'local', generatedID: 3),
    );

    final resolved = result as ResolvedExistingUpload;
    expect(resolved.file, same(destinationMatch));
    expect(resolved.match, ExistingUploadMatch.sameLocalSameCollection);
    expect(actions.assignedLocalIDs, isEmpty);
  });

  test('does not mutate a missing local ID when persistence fails', () async {
    final existing = _file(uploadedFileID: 1, collectionID: 10);
    final error = StateError('write failed');
    final actions = _FakeExistingUploadActions()
      ..matches = [existing]
      ..assignError = error;
    final resolver = ExistingUploadResolver(actions: actions);

    await expectLater(
      _resolve(resolver, _file(localID: 'local', generatedID: 2)),
      throwsA(same(error)),
    );

    expect(existing.localID, isNull);
    expect(actions.deletedGeneratedIDs, isEmpty);
    expect(actions.notifications, isEmpty);
  });

  test('links a matching local file from another collection', () async {
    final existing = _file(
      localID: 'local',
      uploadedFileID: 1,
      collectionID: 20,
    );
    final linked = _file(localID: 'local', uploadedFileID: 1, collectionID: 10);
    final actions = _FakeExistingUploadActions()
      ..matches = [existing]
      ..linkedFile = linked;
    final resolver = ExistingUploadResolver(actions: actions);

    final result = await _resolve(resolver, _file(localID: 'local'));

    final resolved = result as ResolvedExistingUpload;
    expect(resolved.file, same(linked));
    expect(resolved.match, ExistingUploadMatch.linkedToDifferentCollection);
    expect(actions.linkedCollectionID, 10);
  });

  test('matches shared media without comparing local IDs', () async {
    final existing = _file(
      localID: 'other-local',
      uploadedFileID: 1,
      collectionID: 10,
    );
    final actions = _FakeExistingUploadActions()..matches = [existing];
    final resolver = ExistingUploadResolver(actions: actions);

    final result = await _resolve(
      resolver,
      _file(localID: '${sharedMediaIdentifier}source', generatedID: 2),
    );

    final resolved = result as ResolvedExistingUpload;
    expect(resolved.file, same(existing));
    expect(resolved.match, ExistingUploadMatch.sameLocalSameCollection);
  });
}

Future<ExistingUploadResolution> _resolve(
  ExistingUploadResolver resolver,
  EnteFile file,
) {
  return resolver.resolve(
    hashData: FileHashData('hash'),
    fileToUpload: file,
    destinationCollectionID: 10,
    ownerID: 5,
  );
}

EnteFile _file({
  String? localID,
  int? uploadedFileID,
  int? generatedID,
  int? collectionID,
}) {
  return EnteFile()
    ..localID = localID
    ..uploadedFileID = uploadedFileID
    ..generatedID = generatedID
    ..collectionID = collectionID
    ..fileType = FileType.image;
}

class _FakeExistingUploadActions implements ExistingUploadActions {
  List<EnteFile> matches = [];
  List<int> deletedGeneratedIDs = [];
  List<(int, String)> assignedLocalIDs = [];
  List<(EnteFile, String)> notifications = [];
  EnteFile? linkedFile;
  Object? assignError;
  int findCalls = 0;
  int? linkedCollectionID;

  @override
  Future<List<EnteFile>> findUploadedFiles(
    FileHashData hashData,
    FileType fileType,
    int ownerID,
  ) async {
    findCalls++;
    return matches;
  }

  @override
  Future<void> assignLocalID(int uploadedFileID, String localID) async {
    if (assignError != null) {
      throw assignError!;
    }
    assignedLocalIDs.add((uploadedFileID, localID));
  }

  @override
  Future<void> deleteByGeneratedID(int generatedID) async {
    deletedGeneratedIDs.add(generatedID);
  }

  @override
  Future<EnteFile> linkToCollection(
    int destinationCollectionID, {
    required EnteFile localFileToUpload,
    required EnteFile existingUploadedFile,
  }) async {
    linkedCollectionID = destinationCollectionID;
    return linkedFile!;
  }

  @override
  void notifyPendingFileRemoved(EnteFile file, String source) {
    notifications.add((file, source));
  }
}
