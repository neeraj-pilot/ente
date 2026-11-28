import 'package:ente_account_settings/account_settings.dart';
import 'package:ente_components/ente_components.dart';
import 'package:ente_frb/accounts.dart';
import 'package:ente_strings/ente_strings.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

final _strings = lookupStringsLocalizations(const Locale('en'));

class _FakeHost implements AccountSettingsHost {
  final setEmailMfaCalls = <bool>[];
  final passwords = <String>[];
  bool srpConfigured = false;

  @override
  String? getEmail() => 'user@example.org';

  @override
  Future<void> setEmailMfa(bool enabled) async {
    setEmailMfaCalls.add(enabled);
    if (!enabled && !srpConfigured) {
      throw const AccountsError.srpNotConfigured();
    }
  }

  @override
  Future<void> setupSrp(String password) async {
    passwords.add(password);
    if (password != 'correct horse') {
      throw const AccountsError.incorrectPassword();
    }
    srpConfigured = true;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<BuildContext> _pumpApp(WidgetTester tester, _FakeHost host) async {
  AccountSettings.instance.init(host: host);
  late BuildContext context;
  await tester.pumpWidget(
    MaterialApp(
      theme: ComponentTheme.lightTheme(),
      localizationsDelegates: StringsLocalizations.localizationsDelegates,
      supportedLocales: StringsLocalizations.supportedLocales,
      home: Builder(
        builder: (builderContext) {
          context = builderContext;
          return const SizedBox();
        },
      ),
    ),
  );
  return context;
}

Future<void> _expectPasswordPage(WidgetTester tester) async {
  await tester.pumpAndSettle();
  expect(find.text(_strings.enterPassword), findsOneWidget);
  expect(find.text('user@example.org'), findsOneWidget);
}

void main() {
  testWidgets('backing out of the password page leaves the setting alone', (
    tester,
  ) async {
    final host = _FakeHost();
    final context = await _pumpApp(tester, host);
    final done = setEmailVerification(context, false);
    await _expectPasswordPage(tester);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    await done;

    expect(host.setEmailMfaCalls, [false]);
    expect(host.passwords, isEmpty);
  });

  testWidgets('a wrong password stays on the page, the right one retries', (
    tester,
  ) async {
    final host = _FakeHost();
    final context = await _pumpApp(tester, host);
    final done = setEmailVerification(context, false);
    await _expectPasswordPage(tester);

    await tester.enterText(find.byType(TextField), 'wrong');
    await tester.pump();
    await tester.tap(find.text(_strings.verifyPassword));
    await tester.pumpAndSettle();
    expect(find.text(_strings.incorrectPasswordTitle), findsOneWidget);
    expect(find.text(_strings.enterPassword), findsOneWidget);
    expect(host.setEmailMfaCalls, [false]);

    await tester.enterText(find.byType(TextField), 'correct horse');
    await tester.pump();
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(find.text(_strings.enterPassword), findsNothing);
    expect(host.passwords, ['wrong', 'correct horse']);
    expect(host.setEmailMfaCalls, [false, false]);
    await done;
  });
}
