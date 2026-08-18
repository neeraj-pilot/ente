import "package:flutter_test/flutter_test.dart";
import "package:photos/models/file/file.dart";
import "package:photos/services/filter/collection_ignore.dart";

void main() {
  test("filters hidden collection memberships and already-owned shares", () {
    const ownerID = 1;
    const ignoredCollectionID = 99;
    final ownedVisible = _file(1, ownerID, 10, "saved");
    final sharedSaved = _file(2, 2, 20, "saved");
    final ownedHidden = _file(3, ownerID, ignoredCollectionID, "hidden");
    final sharedHiddenHash = _file(4, 2, 20, "hidden");
    final hiddenMembership = _file(5, ownerID, ignoredCollectionID, "other");
    final visibleMembership = _file(5, ownerID, 10, "other");
    final localHidden = EnteFile()..collectionID = ignoredCollectionID;
    final localVisible = EnteFile()..collectionID = 10;
    final files = [
      ownedVisible,
      sharedSaved,
      ownedHidden,
      sharedHiddenHash,
      hiddenMembership,
      visibleMembership,
      localHidden,
      localVisible,
    ];

    final filter = CollectionsAndSavedFileFilter(
      {ignoredCollectionID},
      ownerID,
      files,
      true,
    );

    expect(files.map(filter.filter), [
      true,
      false,
      false,
      true,
      false,
      false,
      false,
      true,
    ]);
  });
}

EnteFile _file(int uploadID, int ownerID, int collectionID, String hash) =>
    EnteFile()
      ..uploadedFileID = uploadID
      ..ownerID = ownerID
      ..collectionID = collectionID
      ..hash = hash;
