import 'package:computer/computer.dart';
import 'package:ente_crypto/ente_crypto.dart';
import 'package:ente_crypto_example/screens/test_runner_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    await Computer.shared().turnOn(workersCount: 4);
    CryptoUtil.init();
  });

  group('Crypto Integration Tests', () {
    testWidgets('Test Runner UI loads correctly', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: TestRunnerScreen(),
      ));

      expect(find.text('Ente Crypto Test Runner'), findsOneWidget);
      expect(find.text('Basic Crypto'), findsOneWidget);
      expect(find.text('Streaming'), findsOneWidget);
      expect(find.text('Key Derivation'), findsOneWidget);
      expect(find.text('File Operations'), findsOneWidget);

      expect(find.byIcon(Icons.play_arrow), findsOneWidget);
    });

    testWidgets('Run Basic Crypto tests', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: TestRunnerScreen(),
      ));

      final basicCryptoTile = find.text('Basic Crypto');
      expect(basicCryptoTile, findsOneWidget);

      await tester.tap(basicCryptoTile);
      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsOneWidget);

      await tester.pumpAndSettle(const Duration(seconds: 10));

      expect(find.text('Basic Crypto Results'), findsOneWidget);
      expect(find.byIcon(Icons.check_circle), findsAtLeastNWidgets(1));
    });

    testWidgets('Run Streaming tests', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: TestRunnerScreen(),
      ));

      final streamingTile = find.text('Streaming');
      expect(streamingTile, findsOneWidget);

      await tester.tap(streamingTile);
      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsOneWidget);

      await tester.pumpAndSettle(const Duration(seconds: 30));

      expect(find.text('Streaming Results'), findsOneWidget);
      expect(find.text('Total'), findsOneWidget);
      expect(find.text('Passed'), findsOneWidget);
    });

    testWidgets('Run Key Derivation tests', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: TestRunnerScreen(),
      ));

      final keyDerivationTile = find.text('Key Derivation');
      expect(keyDerivationTile, findsOneWidget);

      await tester.tap(keyDerivationTile);
      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsOneWidget);

      await tester.pumpAndSettle(const Duration(seconds: 15));

      expect(find.text('Key Derivation Results'), findsOneWidget);
      expect(find.byIcon(Icons.check_circle), findsAtLeastNWidgets(1));
    });

    testWidgets('Run File Operations tests', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: TestRunnerScreen(),
      ));

      final fileOpsTile = find.text('File Operations');
      expect(fileOpsTile, findsOneWidget);

      await tester.tap(fileOpsTile);
      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsOneWidget);

      await tester.pumpAndSettle(const Duration(seconds: 20));

      expect(find.text('File Operations Results'), findsOneWidget);
      expect(find.text('Total'), findsOneWidget);
    });

    testWidgets('Run all tests', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: TestRunnerScreen(),
      ));

      final runAllButton = find.byIcon(Icons.play_arrow);
      expect(runAllButton, findsOneWidget);

      await tester.tap(runAllButton);
      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsAtLeastNWidgets(1));

      await tester.pumpAndSettle(const Duration(minutes: 2));

      expect(find.text('All Tests Results'), findsOneWidget);
      expect(find.text('Total'), findsOneWidget);
      expect(find.text('Passed'), findsOneWidget);
      expect(find.text('Failed'), findsOneWidget);
    });

    testWidgets('Test result details expansion', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: TestRunnerScreen(),
      ));

      final basicCryptoTile = find.text('Basic Crypto');
      await tester.tap(basicCryptoTile);
      await tester.pumpAndSettle(const Duration(seconds: 10));

      final firstTestResult = find.byType(ExpansionTile).first;
      await tester.tap(firstTestResult);
      await tester.pumpAndSettle();

      expect(find.textContaining('Duration:'), findsAtLeastNWidgets(1));
    });

    testWidgets('Copy test results', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: TestRunnerScreen(),
      ));

      final basicCryptoTile = find.text('Basic Crypto');
      await tester.tap(basicCryptoTile);
      await tester.pumpAndSettle(const Duration(seconds: 10));

      final copyButton = find.byIcon(Icons.copy);
      expect(copyButton, findsOneWidget);

      await tester.tap(copyButton);
      await tester.pumpAndSettle();

      expect(find.text('Results copied to clipboard'), findsOneWidget);
    });

    testWidgets('Test status indicators update correctly',
        (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: TestRunnerScreen(),
      ));

      final notStartedIndicator = find.text('Not Started');
      expect(notStartedIndicator, findsOneWidget);

      final basicCryptoTile = find.text('Basic Crypto');
      await tester.tap(basicCryptoTile);
      await tester.pump();

      final runningIndicator = find.text('Running');
      expect(runningIndicator, findsOneWidget);

      await tester.pumpAndSettle(const Duration(seconds: 10));

      final passedIndicator = find.text('Passed');
      expect(passedIndicator, findsOneWidget);
    });
  });
}
