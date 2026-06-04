import "package:flutter/material.dart";
import "package:flutter_test/flutter_test.dart";
import "package:photos/ente_theme_data.dart";
import "package:photos/generated/intl/app_localizations.dart";
import "package:photos/models/file/file.dart";
import "package:photos/models/file/file_type.dart";
import "package:photos/models/ignored_file.dart";
import "package:photos/models/ignored_upload.dart";
import "package:photos/models/ignored_upload_reason.dart";
import "package:photos/ui/viewer/gallery/ignored_uploads_page.dart";
import "package:photos/utils/apple_photos_errors.dart";

void main() {
  group("IgnoredUploadsContent", () {
    testWidgets("filters ignored uploads by reason chips", (tester) async {
      var selectedBucket = IgnoredUploadReasonBucket.all;
      final retriedLocalIDs = <String>[];

      await tester.pumpWidget(
        _buildTestApp(
          StatefulBuilder(
            builder: (context, setState) {
              return IgnoredUploadsContent(
                uploads: _uploads,
                selectedBucket: selectedBucket,
                showThumbnails: false,
                onBucketChanged: (bucket) {
                  setState(() {
                    selectedBucket = bucket;
                  });
                },
                onRetryUpload: (upload) async {
                  retriedLocalIDs.add(upload.file.localID!);
                },
                onRetryUploads: (uploads) async {
                  retriedLocalIDs.addAll(
                    uploads.map((upload) => upload.file.localID!),
                  );
                },
              );
            },
          ),
        ),
      );

      expect(
        find.byKey(const ValueKey("ignored_upload_reason_bucket_all")),
        findsOneWidget,
      );
      expect(
        find.byKey(
          const ValueKey("ignored_upload_reason_bucket_iCloudUnavailable"),
        ),
        findsOneWidget,
      );
      expect(
        find.byKey(
          const ValueKey("ignored_upload_reason_bucket_deletedFromEnte"),
        ),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey("ignored_upload_reason_bucket_other")),
        findsOneWidget,
      );
      expect(find.text("icloud.mov"), findsOneWidget);
      expect(find.text("deleted.jpg"), findsOneWidget);
      expect(find.text("unknown.png"), findsOneWidget);

      await tester.tap(
        find.byKey(
          const ValueKey("ignored_upload_reason_bucket_iCloudUnavailable"),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text("icloud.mov"), findsOneWidget);
      expect(find.text("deleted.jpg"), findsNothing);
      expect(find.text("unknown.png"), findsNothing);

      await tester.tap(find.text("Retry shown"));
      await tester.pumpAndSettle();

      expect(retriedLocalIDs, ["icloud"]);
    });

    testWidgets("retries a single ignored upload", (tester) async {
      final retriedLocalIDs = <String>[];

      await tester.pumpWidget(
        _buildTestApp(
          IgnoredUploadsContent(
            uploads: _uploads,
            selectedBucket: IgnoredUploadReasonBucket.all,
            showThumbnails: false,
            onBucketChanged: (_) {},
            onRetryUpload: (upload) async {
              retriedLocalIDs.add(upload.file.localID!);
            },
            onRetryUploads: (_) async {},
          ),
        ),
      );

      await tester.tap(find.byTooltip("Retry").first);
      await tester.pumpAndSettle();

      expect(retriedLocalIDs, ["icloud"]);
    });
  });
}

Widget _buildTestApp(Widget child) {
  return MaterialApp(
    theme: darkThemeData,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(body: SizedBox(width: 360, child: child)),
  );
}

List<IgnoredUpload> get _uploads => [
  IgnoredUpload(
    file: _file("icloud", "icloud.mov", FileType.video),
    reason: phPhotosResourceUnavailableReason,
  ),
  IgnoredUpload(
    file: _file("deleted", "deleted.jpg", FileType.image),
    reason: kIgnoreReasonTrash,
  ),
  IgnoredUpload(
    file: _file("unknown", "unknown.png", FileType.image),
    reason: "thumbnailMissing",
  ),
];

EnteFile _file(String localID, String title, FileType fileType) {
  return EnteFile()
    ..localID = localID
    ..title = title
    ..deviceFolder = "Camera"
    ..fileType = fileType;
}
