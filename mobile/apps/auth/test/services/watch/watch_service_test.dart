import 'package:ente_auth/models/watch/state.dart';
import 'package:ente_auth/services/watch/selection.dart';
import 'package:ente_auth/services/watch/service.dart';
import 'package:ente_auth/ui/settings/watch_settings_page.dart';
import 'package:ente_components/ente_components.dart';
import 'package:ente_strings/ente_strings.dart';
import 'package:ente_ui/theme/theme_config.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const methodChannel = MethodChannel('io.ente.auth/watch');
  const eventChannel = MethodChannel('io.ente.auth/watch/state');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUpAll(() {
    AppThemeConfig.initialize(EnteApp.auth);
    ComponentTheme.configure(app: ComponentApp.auth);
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
}
