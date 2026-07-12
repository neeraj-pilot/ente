import 'package:collection/collection.dart';
import 'package:logging/logging.dart';
import 'package:photos/core/event_bus.dart';
import 'package:photos/db/files_db.dart';
import 'package:photos/events/files_updated_event.dart';
import 'package:photos/events/local_photos_updated_event.dart';
import 'package:photos/models/file/file.dart';
import 'package:photos/models/file/file_type.dart';
import 'package:photos/module/upload/model/media_upload_data.dart';
import 'package:photos/services/collections_service.dart';

class ExistingUploadResolver {
  ExistingUploadResolver({ExistingUploadActions? actions})
    : _actions = actions ?? _PhotosExistingUploadActions();

  final ExistingUploadActions _actions;
  final _logger = Logger('ExistingUploadResolver');

  Future<ExistingUploadResolution> resolve({
    required FileHashData hashData,
    required EnteFile fileToUpload,
    required int destinationCollectionID,
    required int ownerID,
  }) async {
    if (fileToUpload.uploadedFileID != null) {
      _logger.severe('Critical: file is already uploaded, skipped mapping');
      return const UploadRequired();
    }

    final existingUploadedFiles = await _actions.findUploadedFiles(
      hashData,
      fileToUpload.fileType,
      ownerID,
    );
    if (existingUploadedFiles.isEmpty) {
      return const UploadRequired();
    }

    final isSandboxFile = fileToUpload.isSharedMediaToAppSandbox;
    final sameLocalSameCollection = existingUploadedFiles.firstWhereOrNull(
      (file) =>
          file.collectionID == destinationCollectionID &&
          (file.localID == fileToUpload.localID || isSandboxFile),
    );
    if (sameLocalSameCollection != null) {
      _logger.info(
        'sameLocalSameCollection: toUpload ${fileToUpload.tag} '
        'existing: ${sameLocalSameCollection.tag} $isSandboxFile',
      );
      await _deletePendingFile(fileToUpload, 'sameLocalSameCollection');
      return ResolvedExistingUpload(
        sameLocalSameCollection,
        ExistingUploadMatch.sameLocalSameCollection,
      );
    }

    final fileMissingLocal = existingUploadedFiles.firstWhereOrNull(
      (file) => file.localID == null,
    );
    if (fileMissingLocal != null) {
      _logger.info(
        'fileMissingLocal: toUpload ${fileToUpload.tag} '
        'existing: ${fileMissingLocal.tag}',
      );
      await _actions.assignLocalID(
        fileMissingLocal.uploadedFileID!,
        fileToUpload.localID!,
      );
      await _deletePendingFile(fileToUpload, 'fileMissingLocal');
      fileMissingLocal.localID = fileToUpload.localID;
      return ResolvedExistingUpload(
        fileMissingLocal,
        ExistingUploadMatch.adoptedMissingLocalID,
      );
    }

    final fileInAnotherCollection = existingUploadedFiles.firstWhereOrNull(
      (file) =>
          file.collectionID != destinationCollectionID &&
          (file.localID == fileToUpload.localID || isSandboxFile),
    );
    if (fileInAnotherCollection != null) {
      _logger.info(
        'fileExistsButDifferentCollection: toUpload ${fileToUpload.tag} '
        'existing: ${fileInAnotherCollection.tag} $isSandboxFile',
      );
      final linkedFile = await _actions.linkToCollection(
        destinationCollectionID,
        localFileToUpload: fileToUpload,
        existingUploadedFile: fileInAnotherCollection,
      );
      return ResolvedExistingUpload(
        linkedFile,
        ExistingUploadMatch.linkedToDifferentCollection,
      );
    }

    final matchLocalIDs = existingUploadedFiles
        .map((file) => file.localID)
        .nonNulls
        .toSet();
    _logger.info('Found hash match with different local IDs $matchLocalIDs');
    return const UploadRequired();
  }

  Future<void> _deletePendingFile(EnteFile file, String source) async {
    if (file.generatedID != null) {
      await _actions.deleteByGeneratedID(file.generatedID!);
    }
    _actions.notifyPendingFileRemoved(file, source);
  }
}

abstract interface class ExistingUploadActions {
  Future<List<EnteFile>> findUploadedFiles(
    FileHashData hashData,
    FileType fileType,
    int ownerID,
  );

  Future<void> assignLocalID(int uploadedFileID, String localID);

  Future<void> deleteByGeneratedID(int generatedID);

  Future<EnteFile> linkToCollection(
    int destinationCollectionID, {
    required EnteFile localFileToUpload,
    required EnteFile existingUploadedFile,
  });

  void notifyPendingFileRemoved(EnteFile file, String source);
}

class _PhotosExistingUploadActions implements ExistingUploadActions {
  @override
  Future<List<EnteFile>> findUploadedFiles(
    FileHashData hashData,
    FileType fileType,
    int ownerID,
  ) {
    return FilesDB.instance.getUploadedFilesWithHashes(
      hashData,
      fileType,
      ownerID,
    );
  }

  @override
  Future<void> assignLocalID(int uploadedFileID, String localID) {
    return FilesDB.instance.updateLocalIDForUploaded(uploadedFileID, localID);
  }

  @override
  Future<void> deleteByGeneratedID(int generatedID) {
    return FilesDB.instance.deleteByGeneratedID(generatedID);
  }

  @override
  Future<EnteFile> linkToCollection(
    int destinationCollectionID, {
    required EnteFile localFileToUpload,
    required EnteFile existingUploadedFile,
  }) {
    return CollectionsService.instance
        .linkLocalFileToExistingUploadedFileInAnotherCollection(
          destinationCollectionID,
          localFileToUpload: localFileToUpload,
          existingUploadedFile: existingUploadedFile,
        );
  }

  @override
  void notifyPendingFileRemoved(EnteFile file, String source) {
    Bus.instance.fire(
      LocalPhotosUpdatedEvent(
        [file],
        type: EventType.deletedFromEverywhere,
        source: source,
      ),
    );
  }
}

sealed class ExistingUploadResolution {
  const ExistingUploadResolution();
}

final class UploadRequired extends ExistingUploadResolution {
  const UploadRequired();
}

final class ResolvedExistingUpload extends ExistingUploadResolution {
  const ResolvedExistingUpload(this.file, this.match);

  final EnteFile file;
  final ExistingUploadMatch match;
}

enum ExistingUploadMatch {
  sameLocalSameCollection,
  adoptedMissingLocalID,
  linkedToDifferentCollection,
}
