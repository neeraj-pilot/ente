import 'package:flutter/services.dart';

class DeviceTrashEntry {
  const DeviceTrashEntry({
    required this.mediaStoreId,
    required this.volumeName,
    required this.expiresAt,
    required this.bucketName,
  });

  factory DeviceTrashEntry.fromMap(Map<Object?, Object?> map) {
    return DeviceTrashEntry(
      mediaStoreId: map['mediaStoreId']! as int,
      volumeName: map['volumeName']! as String,
      expiresAt: DateTime.fromMicrosecondsSinceEpoch(
        map['expiresAtUs']! as int,
        isUtc: true,
      ),
      bucketName: map['bucketName'] as String?,
    );
  }

  final int mediaStoreId;
  final String volumeName;
  final DateTime expiresAt;
  final String? bucketName;
}

class DeviceTrashClient {
  DeviceTrashClient({MethodChannel? methodChannel})
    : _methodChannel = methodChannel ?? const MethodChannel(_channelName);

  static final instance = DeviceTrashClient();
  static const _channelName =
      'io.ente.photos.platform/device_trash/commands.v1';

  final MethodChannel _methodChannel;

  Future<List<DeviceTrashEntry>> getFiles() async {
    final files =
        await _methodChannel.invokeListMethod<Map<Object?, Object?>>(
          'getFiles',
        ) ??
        const [];
    return files.map(DeviceTrashEntry.fromMap).toList(growable: false);
  }
}
