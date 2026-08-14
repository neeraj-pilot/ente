import 'package:ente_photos_platform/ente_photos_platform.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('device_trash_test');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('maps named native fields without positional coupling', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getFiles');
      expect(call.arguments, isNull);
      return [
        {
          'mediaStoreId': 17,
          'volumeName': 'external_primary',
          'expiresAtUs': 1800000000000000,
          'bucketName': 'Camera',
        },
        {
          'mediaStoreId': 29,
          'volumeName': '0123-4567',
          'expiresAtUs': 1900000000000000,
          'bucketName': null,
        },
      ];
    });

    final files = await DeviceTrashClient(methodChannel: channel).getFiles();

    expect(files, hasLength(2));
    expect(files.first.mediaStoreId, 17);
    expect(files.first.volumeName, 'external_primary');
    expect(
      files.first.expiresAt,
      DateTime.fromMicrosecondsSinceEpoch(1800000000000000, isUtc: true),
    );
    expect(files.first.bucketName, 'Camera');
    expect(files.last.mediaStoreId, 29);
    expect(files.last.volumeName, '0123-4567');
    expect(files.last.bucketName, isNull);
  });
}
