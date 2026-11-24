import 'dart:convert';
import 'dart:io';

import 'package:ente_auth/core/configuration.dart';
import 'package:ente_auth/models/code.dart';
import 'package:ente_auth/models/code_display.dart';
import 'package:ente_auth/models/watch/state.dart';
import 'package:ente_auth/services/preference_service.dart';
import 'package:ente_auth/services/watch/selection.dart';
import 'package:ente_auth/services/watch/service.dart';
import 'package:ente_auth/ui/settings/watch_settings_page.dart';
import 'package:ente_components/ente_components.dart';
import 'package:ente_strings/ente_strings.dart';
import 'package:ente_ui/theme/theme_config.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const methodChannel = MethodChannel('io.ente.auth/watch');
  const eventChannel = MethodChannel('io.ente.auth/watch/state');
  const pathsChannel = MethodChannel('plugins.flutter.io/path_provider');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUpAll(() async {
    final root = await Directory.systemTemp.createTemp('watch_service_test_');
    messenger.setMockMethodCallHandler(pathsChannel, (_) async => root.path);
    addTearDown(() async {
      messenger.setMockMethodCallHandler(pathsChannel, null);
      await root.delete(recursive: true);
    });
    SharedPreferences.setMockInitialValues({
      Configuration.hasOptedForOfflineModeKey: true,
    });
    FlutterSecureStorage.setMockInitialValues({});
    AppThemeConfig.initialize(EnteApp.auth);
    ComponentTheme.configure(app: ComponentApp.auth);
    await Configuration.instance.init([]);
  });

  tearDown(() {
    WatchService.instanceOrNull?.dispose();
    messenger.setMockMethodCallHandler(methodChannel, null);
    messenger.setMockMethodCallHandler(eventChannel, null);
  });

  test(
    'WatchService marks platform unsupported on MissingPluginException',
    () async {
      SharedPreferences.setMockInitialValues({});
      messenger.setMockMethodCallHandler(methodChannel, null);

      final service = await WatchService.initializeForTest();
      expect(service.isPlatformSupported, false);
      expect(service.state.phase, WatchPhase.unavailable);
    },
  );

  test('WatchService clears selection and notifies native on logout', () async {
    SharedPreferences.setMockInitialValues({
      'watchSelection': WatchSelection(
        scope: 'offline',
        epoch: 'epoch-1',
        watches: {'watch-1': const WatchSharing(mode: WatchSharingMode.all)},
      ).encode(),
    });
    final calls = <String>[];
    messenger.setMockMethodCallHandler(methodChannel, (call) async {
      calls.add(call.method);
      return {
        'phase': 'setup',
        'enrollmentID': null,
        'requestID': null,
        'vaultEpoch': 'epoch-1',
      };
    });
    messenger.setMockMethodCallHandler(eventChannel, (_) async => null);

    final service = await WatchService.initializeForTest();
    expect(service.isPlatformSupported, true);
    expect(service.state.phase, WatchPhase.setup);

    WatchService.appLockChanged(true);
    await WatchService.clearBeforeLogout();

    expect(calls, contains('clear'));
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('watchSelection'), isNull);
  });

  testWidgets(
    'WatchSettingsPage hides Check again when platform bridge is unsupported',
    timeout: const Timeout(Duration(seconds: 30)),
    (tester) async {
      await tester.runAsync(() async {
        SharedPreferences.setMockInitialValues({});
        messenger.setMockMethodCallHandler(methodChannel, null);
        await WatchService.initializeForTest();
      });

      await tester.pumpWidget(
        const MaterialApp(
          localizationsDelegates: StringsLocalizations.localizationsDelegates,
          supportedLocales: StringsLocalizations.supportedLocales,
          home: WatchSettingsPage(),
        ),
      );
      await tester.pump();

      expect(find.text('Check again'), findsNothing);
    },
  );

  testWidgets(
    'WatchSettingsPage shows Check again when bridge is supported but unavailable',
    timeout: const Timeout(Duration(seconds: 30)),
    (tester) async {
      await tester.runAsync(() async {
        SharedPreferences.setMockInitialValues({});
        messenger.setMockMethodCallHandler(methodChannel, (call) async {
          return {
            'phase': 'unavailable',
            'enrollmentID': null,
            'requestID': null,
            'vaultEpoch': null,
          };
        });
        messenger.setMockMethodCallHandler(eventChannel, (_) async => null);
        await WatchService.initializeForTest();
      });

      await tester.pumpWidget(
        const MaterialApp(
          localizationsDelegates: StringsLocalizations.localizationsDelegates,
          supportedLocales: StringsLocalizations.supportedLocales,
          home: WatchSettingsPage(),
        ),
      );
      await tester.pump();

      expect(find.text('Check again'), findsOneWidget);
    },
  );

  test('WatchService reconciles shared tags on rename and deletion', () async {
    SharedPreferences.setMockInitialValues({
      Configuration.hasOptedForOfflineModeKey: true,
      'watchSelection': WatchSelection(
        scope: 'offline',
        epoch: 'epoch-1',
        watches: {
          'watch-1': const WatchSharing(
            mode: WatchSharingMode.tags,
            tags: {'Work', 'Personal'},
          ),
        },
      ).encode(),
    });
    messenger.setMockMethodCallHandler(methodChannel, (call) async {
      return {
        'phase': 'synced',
        'enrollmentID': 'watch-1',
        'requestID': 'watch-1',
        'vaultEpoch': 'epoch-1',
      };
    });
    messenger.setMockMethodCallHandler(eventChannel, (_) async => null);

    final service = await WatchService.initializeForTest();
    service.didChangeAppLifecycleState(AppLifecycleState.resumed);
    await service.refresh();
    expect(service.sharing?.tags, {'Work', 'Personal'});

    await WatchService.onTagRenamed('Work', 'Office');
    expect(service.sharing?.tags, {'Office', 'Personal'});
    expect(WatchService.sharedTags, {'Office', 'Personal'});

    await WatchService.onTagDeleted('Office');
    expect(service.sharing?.tags, {'Personal'});
    expect(WatchService.sharedTags, {'Personal'});
  });

  test(
    'WatchService publishes only pinned codes and reflects shouldHideCodes',
    () async {
      final pinnedCode = Code.fromAccountAndSecret(
        Type.totp,
        'pinned@example.com',
        'Pinned',
        'JBSWY3DPEHPK3PXP',
        CodeDisplay(pinned: true, tags: ['Work']),
        6,
      )..generatedID = 1;
      final regularCode = Code.fromAccountAndSecret(
        Type.totp,
        'regular@example.com',
        'Regular',
        'JBSWY3DPEHPK3PXP',
        CodeDisplay(pinned: false, tags: ['Work']),
        6,
      )..generatedID = 2;

      SharedPreferences.setMockInitialValues({
        Configuration.hasOptedForOfflineModeKey: true,
        PreferenceService.kShouldHideCodesKey: true,
        'watchSelection': WatchSelection(
          scope: 'offline',
          epoch: 'epoch-1',
          watches: {
            'watch-1': const WatchSharing(mode: WatchSharingMode.pinned),
          },
        ).encode(),
      });

      final publishedSnapshots = <Map<String, dynamic>>[];
      messenger.setMockMethodCallHandler(methodChannel, (call) async {
        if (call.method == 'publish') {
          final args = call.arguments as Map<Object?, Object?>;
          final raw = args['snapshot'] as Uint8List;
          publishedSnapshots.add(
            jsonDecode(utf8.decode(raw)) as Map<String, dynamic>,
          );
        }
        return {
          'phase': 'synced',
          'enrollmentID': 'watch-1',
          'requestID': 'watch-1',
          'vaultEpoch': 'epoch-1',
        };
      });
      messenger.setMockMethodCallHandler(eventChannel, (_) async => null);

      final service = await WatchService.initializeForTest(
        loadCodes: () async => [pinnedCode, regularCode],
      );
      service.didChangeAppLifecycleState(AppLifecycleState.resumed);
      await service.refresh();

      expect(publishedSnapshots, isNotEmpty);
      final latest = publishedSnapshots.last;
      expect(latest['hideCodes'], true);
      final entries = (latest['entries'] as List).cast<Map<String, dynamic>>();
      expect(entries.length, 1);
      expect(entries[0]['issuer'], 'Pinned');
      expect(entries[0]['pinned'], true);
      expect(entries[0]['tags'], ['Work']);
    },
  );
}
