import "package:flutter_test/flutter_test.dart";
import "package:photos/models/ignored_file.dart";
import "package:photos/models/ignored_upload_reason.dart";
import "package:photos/utils/apple_photos_errors.dart";

void main() {
  group("ignoredUploadReasonBucketFor", () {
    test("maps iCloud PhotoKit failures to iCloud unavailable", () {
      expect(
        ignoredUploadReasonBucketFor(phPhotosResourceUnavailableReason),
        IgnoredUploadReasonBucket.iCloudUnavailable,
      );
    });

    test("maps trash ignores to deleted from Ente", () {
      expect(
        ignoredUploadReasonBucketFor(kIgnoreReasonTrash),
        IgnoredUploadReasonBucket.deletedFromEnte,
      );
    });

    test("maps unknown reasons to other", () {
      expect(
        ignoredUploadReasonBucketFor("thumbnailMissing"),
        IgnoredUploadReasonBucket.other,
      );
    });
  });
}
