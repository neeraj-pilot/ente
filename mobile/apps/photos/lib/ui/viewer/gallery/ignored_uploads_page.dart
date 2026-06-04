import "dart:async";

import "package:ente_components/ente_components.dart";
import "package:ente_pure_utils/ente_pure_utils.dart";
import "package:flutter/material.dart";
import "package:photos/generated/l10n.dart";
import "package:photos/models/file/file.dart";
import "package:photos/models/ignored_upload.dart";
import "package:photos/models/ignored_upload_reason.dart";
import "package:photos/services/ignored_files_service.dart";
import "package:photos/services/sync/remote_sync_service.dart";
import "package:photos/theme/ente_theme.dart";
import "package:photos/ui/common/loading_widget.dart";
import "package:photos/ui/viewer/file/detail_page.dart";
import "package:photos/ui/viewer/file/thumbnail_widget.dart";

class IgnoredUploadsPage extends StatefulWidget {
  final Future<List<EnteFile>> filesInDeviceCollection;
  final VoidCallback onIgnoredUploadsChanged;

  const IgnoredUploadsPage({
    required this.filesInDeviceCollection,
    required this.onIgnoredUploadsChanged,
    super.key,
  });

  @override
  State<IgnoredUploadsPage> createState() => _IgnoredUploadsPageState();
}

class _IgnoredUploadsPageState extends State<IgnoredUploadsPage> {
  late Future<List<IgnoredUpload>> _ignoredUploadsFuture;
  IgnoredUploadReasonBucket _selectedBucket = IgnoredUploadReasonBucket.all;

  @override
  void initState() {
    super.initState();
    _ignoredUploadsFuture = _loadIgnoredUploads();
  }

  Future<List<IgnoredUpload>> _loadIgnoredUploads() async {
    final files = await widget.filesInDeviceCollection;
    final idToReasonMap =
        await IgnoredFilesService.instance.idToIgnoreReasonMap;
    return IgnoredFilesService.instance.getIgnoredUploads(idToReasonMap, files);
  }

  Future<void> _retryUploads(List<IgnoredUpload> uploads) async {
    if (uploads.isEmpty) {
      return;
    }
    await IgnoredFilesService.instance.removeIgnoredMappings(
      uploads.map((upload) => upload.file).toList(),
    );
    if (!mounted) {
      return;
    }
    widget.onIgnoredUploadsChanged();
    unawaited(RemoteSyncService.instance.sync(silently: true));
    setState(() {
      _ignoredUploadsFuture = _loadIgnoredUploads();
    });
  }

  @override
  Widget build(BuildContext context) {
    final colors = getEnteColorScheme(context);
    final textTheme = getEnteTextTheme(context);
    return Scaffold(
      backgroundColor: colors.backgroundColour,
      appBar: AppBar(
        elevation: 0,
        centerTitle: false,
        title: Text(
          AppLocalizations.of(context).ignoredUploads,
          style: textTheme.largeBold,
        ),
      ),
      body: FutureBuilder<List<IgnoredUpload>>(
        future: _ignoredUploadsFuture,
        builder: (context, snapshot) {
          if (!snapshot.hasData) {
            return const Center(child: EnteLoadingWidget());
          }
          final uploads = snapshot.data!;
          return IgnoredUploadsContent(
            uploads: uploads,
            selectedBucket: _selectedBucket,
            onBucketChanged: (bucket) {
              setState(() {
                _selectedBucket = bucket;
              });
            },
            onRetryUpload: (upload) => _retryUploads([upload]),
            onRetryUploads: _retryUploads,
          );
        },
      ),
    );
  }
}

class IgnoredUploadsContent extends StatelessWidget {
  final List<IgnoredUpload> uploads;
  final IgnoredUploadReasonBucket selectedBucket;
  final ValueChanged<IgnoredUploadReasonBucket> onBucketChanged;
  final Future<void> Function(IgnoredUpload upload) onRetryUpload;
  final Future<void> Function(List<IgnoredUpload> uploads) onRetryUploads;
  final bool showThumbnails;

  const IgnoredUploadsContent({
    required this.uploads,
    required this.selectedBucket,
    required this.onBucketChanged,
    required this.onRetryUpload,
    required this.onRetryUploads,
    this.showThumbnails = true,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final textTheme = getEnteTextTheme(context);
    final visibleBuckets = _visibleBuckets;
    final effectiveBucket = visibleBuckets.contains(selectedBucket)
        ? selectedBucket
        : IgnoredUploadReasonBucket.all;
    final visibleUploads = effectiveBucket == IgnoredUploadReasonBucket.all
        ? uploads
        : uploads
              .where((upload) => upload.reasonBucket == effectiveBucket)
              .toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
          child: Text(
            AppLocalizations.of(context).ignoredUploadsDescription,
            style: textTheme.smallMuted,
          ),
        ),
        const SizedBox(height: 16),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Row(
            children: [
              for (final bucket in visibleBuckets)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: FilterChipComponent(
                    key: ValueKey(
                      "ignored_upload_reason_bucket_${bucket.name}",
                    ),
                    label: ignoredUploadReasonBucketLabel(context, bucket),
                    state: effectiveBucket == bucket
                        ? FilterChipComponentState.selected
                        : FilterChipComponentState.unselected,
                    onChanged: (_) => onBucketChanged(bucket),
                  ),
                ),
            ],
          ),
        ),
        if (visibleUploads.isNotEmpty) ...[
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Align(
              alignment: Alignment.centerRight,
              child: ButtonComponent(
                label: AppLocalizations.of(context).retryShown,
                size: ButtonComponentSize.small,
                variant: ButtonComponentVariant.secondary,
                onTap: () => onRetryUploads(visibleUploads),
              ),
            ),
          ),
        ],
        const SizedBox(height: 8),
        Expanded(
          child: visibleUploads.isEmpty
              ? const _NoIgnoredUploads()
              : ListView.builder(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                  itemCount: visibleUploads.length,
                  itemBuilder: (context, index) {
                    final upload = visibleUploads[index];
                    return IgnoredUploadRow(
                      upload: upload,
                      onRetryUpload: onRetryUpload,
                      showThumbnail: showThumbnails,
                    );
                  },
                ),
        ),
      ],
    );
  }

  List<IgnoredUploadReasonBucket> get _visibleBuckets {
    final buckets = uploads.map((upload) => upload.reasonBucket).toSet();
    return ignoredUploadReasonBuckets.where((bucket) {
      return bucket == IgnoredUploadReasonBucket.all ||
          buckets.contains(bucket);
    }).toList();
  }
}

class IgnoredUploadRow extends StatelessWidget {
  final IgnoredUpload upload;
  final Future<void> Function(IgnoredUpload upload) onRetryUpload;
  final bool showThumbnail;

  const IgnoredUploadRow({
    required this.upload,
    required this.onRetryUpload,
    this.showThumbnail = true,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final colors = getEnteColorScheme(context);
    final textTheme = getEnteTextTheme(context);
    return GestureDetector(
      onTap: () {
        routeToPage(
          context,
          DetailPage(
            DetailPageConfiguration(
              List.unmodifiable([upload.file]),
              0,
              "ignored_uploads",
            ),
          ),
          forceCustomPageRoute: true,
        );
      },
      child: Container(
        height: 72,
        margin: const EdgeInsets.symmetric(vertical: 6),
        decoration: BoxDecoration(
          color: colors.backgroundElevated,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: colors.strokeFainter),
        ),
        child: Row(
          children: [
            const SizedBox(width: 8),
            _IgnoredUploadThumbnail(
              upload: upload,
              showThumbnail: showThumbnail,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    upload.file.displayName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: textTheme.smallBold,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    ignoredUploadReasonBucketLabel(
                      context,
                      upload.reasonBucket,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: textTheme.miniMuted,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            IconButtonComponent(
              icon: const Icon(Icons.sync, size: IconSizes.small),
              tooltip: AppLocalizations.of(context).retry,
              variant: IconButtonComponentVariant.unfilled,
              onTap: () => onRetryUpload(upload),
            ),
            const SizedBox(width: 8),
          ],
        ),
      ),
    );
  }
}

class _IgnoredUploadThumbnail extends StatelessWidget {
  final IgnoredUpload upload;
  final bool showThumbnail;

  const _IgnoredUploadThumbnail({
    required this.upload,
    required this.showThumbnail,
  });

  @override
  Widget build(BuildContext context) {
    final colors = getEnteColorScheme(context);
    return SizedBox(
      width: 56,
      height: 56,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: showThumbnail
            ? ThumbnailWidget(upload.file, shouldShowSyncStatus: false)
            : DecoratedBox(decoration: BoxDecoration(color: colors.fillFaint)),
      ),
    );
  }
}

class _NoIgnoredUploads extends StatelessWidget {
  const _NoIgnoredUploads();

  @override
  Widget build(BuildContext context) {
    final colors = getEnteColorScheme(context);
    final textTheme = getEnteTextTheme(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 48),
        child: Text(
          AppLocalizations.of(context).noIgnoredUploads,
          textAlign: TextAlign.center,
          style: textTheme.largeMuted.copyWith(color: colors.textMuted),
        ),
      ),
    );
  }
}

String ignoredUploadReasonBucketLabel(
  BuildContext context,
  IgnoredUploadReasonBucket bucket,
) {
  final l10n = AppLocalizations.of(context);
  return switch (bucket) {
    IgnoredUploadReasonBucket.all => l10n.all,
    IgnoredUploadReasonBucket.iCloudUnavailable =>
      l10n.ignoredUploadReasonICloudUnavailable,
    IgnoredUploadReasonBucket.deletedFromEnte =>
      l10n.ignoredUploadReasonDeletedFromEnte,
    IgnoredUploadReasonBucket.other => l10n.other,
  };
}
