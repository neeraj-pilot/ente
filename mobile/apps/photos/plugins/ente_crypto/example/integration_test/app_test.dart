import 'package:computer/computer.dart';
import 'package:ente_crypto/ente_crypto.dart';
import 'package:ente_crypto_example/main.dart' as app;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('App UI Integration Tests', () {
    setUpAll(() async {
      // Initialize crypto utilities
      await Computer.shared().turnOn();
      CryptoUtil.init();
    });

    testWidgets('Should navigate to regression tests and run tests',
        (WidgetTester tester) async {
      // Launch the app
      app.main();
      await tester.pumpAndSettle();

      // Verify we're on the main screen
      expect(find.text('Ente Crypto Test Runner'), findsOneWidget);
      expect(find.text('Regression Tests'), findsOneWidget);

      // Tap on Regression Tests
      await tester.tap(find.text('Regression Tests'));
      await tester.pumpAndSettle();

      // Verify we're on the regression test screen
      expect(find.text('Regression Tests'), findsOneWidget);

      // Wait for test data to load
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      // Check if test data was discovered
      final noDataFinder = find.text('No Test Data Found');
      final runTestsFinder = find.text('Run Tests');

      if (noDataFinder.evaluate().isEmpty) {
        // Test data was found, we should see Run Tests button
        expect(runTestsFinder, findsOneWidget);

        // Tap Run Tests
        await tester.tap(runTestsFinder);
        await tester.pumpAndSettle();

        // Wait for tests to complete
        await tester.pump(const Duration(seconds: 3));
        await tester.pumpAndSettle();

        // Verify test results are displayed
        expect(find.text('Total'), findsOneWidget);
        expect(find.text('Passed'), findsOneWidget);
        expect(find.text('Failed'), findsOneWidget);
        expect(find.text('Pass Rate'), findsOneWidget);

        // Verify we have test result items
        expect(find.byType(Card), findsWidgets);
        expect(find.byIcon(Icons.check_circle), findsWidgets);
      }
    });

    testWidgets('Should display test metadata correctly',
        (WidgetTester tester) async {
      // Launch the app
      app.main();
      await tester.pumpAndSettle();

      // Navigate to Regression Tests
      await tester.tap(find.text('Regression Tests'));
      await tester.pumpAndSettle();

      // Wait for test data to load
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      // Check if metadata is displayed
      final noDataFinder = find.text('No Test Data Found');
      if (noDataFinder.evaluate().isEmpty) {
        // Check for metadata fields
        expect(find.text('Test Data'), findsOneWidget);
        expect(find.text('Platform:'), findsOneWidget);
        expect(find.text('Version:'), findsOneWidget);
        expect(find.text('Test Count:'), findsOneWidget);

        // Should show platform info
        expect(find.textContaining('droid'), findsWidgets);
      }
    });

    testWidgets('Should handle refresh action', (WidgetTester tester) async {
      // Launch the app
      app.main();
      await tester.pumpAndSettle();

      // Navigate to Regression Tests
      await tester.tap(find.text('Regression Tests'));
      await tester.pumpAndSettle();

      // Wait for initial load
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      // Find and tap refresh button
      final refreshFinder = find.widgetWithText(OutlinedButton, 'Refresh');
      if (refreshFinder.evaluate().isNotEmpty) {
        await tester.tap(refreshFinder);
        await tester.pumpAndSettle();

        // Should reload test data
        await tester.pump(const Duration(seconds: 1));
        await tester.pumpAndSettle();

        // Verify screen updated
        expect(find.text('Test Data'), findsOneWidget);
      }
    });
  });
}