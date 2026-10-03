import 'dart:io';
import 'dart:typed_data';

import 'package:ente_auth/services/favicon_service.dart';
import 'package:ente_auth/services/preference_service.dart';
import 'package:ente_auth/ui/settings/general_settings_page.dart';
import 'package:ente_auth/ui/utils/icon_utils.dart';
import 'package:ente_components/ente_components.dart';
import 'package:ente_logging/logging.dart';
import 'package:ente_strings/ente_strings.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final preferences = PreferenceService.instance;
  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    await preferences.init();
    SuperLogging.appVersion = 'test';
    await SuperLogging.main();
  });

  testWidgets(
    'website icons default off and the setting explains network access',
    (tester) async {
      expect(preferences.shouldUseFavicons(), isFalse);
      await tester.pumpWidget(_app(const GeneralSettingsPage()));
      expect(find.text('Use website icons'), findsOneWidget);
      expect(find.textContaining('reveals your IP address'), findsOneWidget);
      expect(find.textContaining('DuckDuckGo'), findsOneWidget);
      expect(find.textContaining('Kagi'), findsOneWidget);
      final row = find.ancestor(
        of: find.text('Use website icons'),
        matching: find.byType(SettingsItem),
      );
      await tester.tap(
        find.descendant(of: row, matching: find.byType(ToggleSwitchComponent)),
      );
      await tester.pumpAndSettle();
      expect(preferences.shouldUseFavicons(), isTrue);
      final saved = await SharedPreferences.getInstance();
      expect(saved.getBool(PreferenceService.kUseFavicons), isTrue);
      await preferences.setUseFavicons(false);
    },
  );

  testWidgets(
    'disabled favicons never fetch; failures keep the bundled fallback',
    (tester) async {
      final network = _UnavailableNetwork();
      final previous = HttpOverrides.current;
      HttpOverrides.global = network;
      addTearDown(() {
        faviconClient.clear();
        HttpOverrides.global = previous;
      });
      Widget icon() => _app(
        Builder(
          builder: (context) => IconUtils.instance.getIcon(
            context,
            'Example',
            domains: const ['example.com'],
          ),
        ),
      );

      await tester.pumpWidget(icon());
      expect(find.byType(FutureBuilder<Uint8List?>), findsNothing);
      expect(network.requests, 0);

      await preferences.setUseFavicons(true);
      await tester.pumpWidget(icon());
      await tester.pumpAndSettle();
      expect(find.byType(FutureBuilder<Uint8List?>), findsOneWidget);
      expect(network.requests, greaterThan(0));
      expect(find.byType(Image), findsNothing);
      expect(tester.takeException(), isNull);

      await preferences.setUseFavicons(false);
      final count = network.requests;
      await tester.pumpWidget(icon());
      expect(find.byType(FutureBuilder<Uint8List?>), findsNothing);
      expect(network.requests, count);
    },
  );
}

Widget _app(Widget child) => MaterialApp(
  theme: ComponentTheme.lightTheme(app: ComponentApp.auth),
  localizationsDelegates: StringsLocalizations.localizationsDelegates,
  supportedLocales: StringsLocalizations.supportedLocales,
  home: Scaffold(body: child),
);

class _UnavailableNetwork extends HttpOverrides {
  var requests = 0;
  @override
  HttpClient createHttpClient(SecurityContext? context) {
    requests++;
    throw const SocketException('Offline');
  }
}
