import 'package:computer/computer.dart';
import 'package:ente_crypto/ente_crypto.dart';
import 'package:ente_crypto_example/test_cases/regression_tests.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    // Initialize crypto utilities
    await Computer.shared().turnOn();
    CryptoUtil.init();
  });

  group('Regression Test Framework Integration Tests', () {
    test('Should discover test data from bundled assets', () async {
      // Act
      final testDataList = await RegressionTestRunner.discoverTestData();

      // If no test data bundled, skip regression tests gracefully
      if (testDataList.isEmpty) {
        print('No test data bundled - regression tests will be skipped');
        return;
      }

      // Assert - test data was found
      expect(testDataList, isNotEmpty,
        reason: 'Should find at least one test data platform');

      // Verify each discovered platform has valid metadata
      for (final platform in testDataList) {
        expect(platform.platform, isNotEmpty);
        expect(platform.version, isNotEmpty);
        expect(platform.testCount, greaterThanOrEqualTo(0));
      }
    });

    test('Should load test runner from discovered platforms', () async {
      // First discover available platforms
      final testDataList = await RegressionTestRunner.discoverTestData();

      if (testDataList.isEmpty) {
        print('No test data bundled - skipping test runner loading test');
        return;
      }

      // Try to load each discovered platform
      for (final platform in testDataList) {
        final runner = await RegressionTestRunner.createFromAssets(platform.path);

        // Assert
        expect(runner, isNotNull,
          reason: 'Should successfully create runner for ${platform.path}');
        expect(runner!.testVectors, isNotEmpty);
        expect(runner.testVectors['test_suites'], isList);
      }
    });

    test('Should run all regression tests successfully', () async {
      // Discover available platforms
      final testDataList = await RegressionTestRunner.discoverTestData();

      if (testDataList.isEmpty) {
        print('No test data bundled - skipping regression test execution');
        return;
      }

      // Run tests for ALL discovered platforms
      for (final platform in testDataList) {
        print('Running regression tests for platform: ${platform.name}');

        final runner = await RegressionTestRunner.createFromAssets(platform.path);
        expect(runner, isNotNull, reason: 'Runner should be created for ${platform.path}');

        // Act
        final results = await runner!.runTests();

        // Assert
        expect(results, isNotEmpty);

        // Check that all tests passed
        final failedTests = results.where((r) => !r.passed).toList();
        if (failedTests.isNotEmpty) {
          final failureDetails = failedTests
            .map((t) => '${t.testId}: ${t.error}')
            .join('\n');
          fail('${failedTests.length} tests failed for ${platform.name}:\n$failureDetails');
        }

        print('✓ All ${results.length} tests passed for ${platform.name}');
      }
    });

    test('Should correctly decrypt XSalsa20 test vectors', () async {
      // Discover and test all platforms
      final testDataList = await RegressionTestRunner.discoverTestData();
      if (testDataList.isEmpty) return;

      for (final platform in testDataList) {
        final runner = await RegressionTestRunner.createFromAssets(platform.path);
        if (runner == null) continue;

        final results = await runner.runTests();
        final xsalsaTests = results.where(
          (r) => r.algorithm == 'XSalsa20-Poly1305'
        ).toList();

        if (xsalsaTests.isNotEmpty) {
          for (final test in xsalsaTests) {
            expect(test.passed, isTrue,
              reason: 'XSalsa20 test ${test.testId} should pass on ${platform.name}');
          }
        }
      }
    });

    test('Should correctly decrypt XChaCha20 test vectors', () async {
      // Arrange
      final runner = await RegressionTestRunner.createFromAssets('droid-photos-v1');
      expect(runner, isNotNull);

      // Act
      final results = await runner!.runTests();

      // Assert - Check XChaCha20 tests specifically
      final chachaTests = results.where(
        (r) => r.algorithm == 'XChaCha20-Poly1305'
      ).toList();

      expect(chachaTests, isNotEmpty);
      expect(chachaTests.length, equals(5),
        reason: 'Should have 5 XChaCha20 test vectors');

      for (final test in chachaTests) {
        expect(test.passed, isTrue,
          reason: 'XChaCha20 test ${test.testId} should pass');
      }
    });

    test('Should correctly derive keys using Argon2id', () async {
      // Arrange
      final runner = await RegressionTestRunner.createFromAssets('droid-photos-v1');
      expect(runner, isNotNull);

      // Act
      final results = await runner!.runTests();

      // Assert - Check Argon2id tests specifically
      final argonTests = results.where(
        (r) => r.algorithm == 'Argon2id'
      ).toList();

      expect(argonTests, isNotEmpty);
      expect(argonTests.length, equals(6),
        reason: 'Should have 6 Argon2id test vectors');

      for (final test in argonTests) {
        expect(test.passed, isTrue,
          reason: 'Argon2id test ${test.testId} should pass');
      }
    });

    test('Should correctly decrypt sealed boxes', () async {
      // Arrange
      final runner = await RegressionTestRunner.createFromAssets('droid-photos-v1');
      expect(runner, isNotNull);

      // Act
      final results = await runner!.runTests();

      // Assert - Check SealedBox tests specifically
      final sealedTests = results.where(
        (r) => r.algorithm == 'SealedBox'
      ).toList();

      expect(sealedTests, isNotEmpty);
      expect(sealedTests.length, equals(5),
        reason: 'Should have 5 SealedBox test vectors');

      for (final test in sealedTests) {
        expect(test.passed, isTrue,
          reason: 'SealedBox test ${test.testId} should pass');
      }
    });

    test('Should correctly decrypt encrypted files', () async {
      // Arrange
      final runner = await RegressionTestRunner.createFromAssets('droid-photos-v1');
      expect(runner, isNotNull);

      // Act
      final results = await runner!.runTests();

      // Assert - Check file decryption tests specifically
      final fileTests = results.where(
        (r) => r.algorithm == 'File-XChaCha20'
      ).toList();

      expect(fileTests, isNotEmpty);
      expect(fileTests.length, equals(5),
        reason: 'Should have 5 file encryption test vectors');

      // Verify each file type
      final expectedFiles = [
        'file_empty_droid',
        'file_small_100b_droid',
        'file_medium_4kb_droid',
        'file_large_1mb_droid',
        'file_chunked_4mb_droid',
      ];

      for (final expectedId in expectedFiles) {
        final test = fileTests.firstWhere(
          (t) => t.testId == expectedId,
          orElse: () => throw TestFailure('Missing test for $expectedId'),
        );
        expect(test.passed, isTrue,
          reason: 'File decryption test $expectedId should pass');
      }
    });

    test('Should handle streaming edge cases', () async {
      // Arrange
      final runner = await RegressionTestRunner.createFromAssets('droid-photos-v1');
      expect(runner, isNotNull);

      // Act
      final results = await runner!.runTests();

      // Assert - Check streaming edge cases
      final streamingTests = results.where(
        (r) => r.algorithm == 'Streaming'
      ).toList();

      expect(streamingTests, isNotEmpty);

      for (final test in streamingTests) {
        expect(test.passed, isTrue,
          reason: 'Streaming edge case ${test.testId} should pass');
      }
    });

    test('Should validate 4MB chunk boundary handling', () async {
      // Arrange
      final runner = await RegressionTestRunner.createFromAssets('droid-photos-v1');
      expect(runner, isNotNull);

      // Act
      final results = await runner!.runTests();

      // Assert - Find the 4MB+ file test
      final chunkBoundaryTest = results.firstWhere(
        (r) => r.testId == 'file_chunked_4mb_droid',
        orElse: () => throw TestFailure('4MB chunk boundary test not found'),
      );

      expect(chunkBoundaryTest.passed, isTrue,
        reason: 'Should correctly handle files crossing 4MB chunk boundary');
      expect(chunkBoundaryTest.algorithm, equals('File-XChaCha20'));
      expect(chunkBoundaryTest.description, contains('4194404'),
        reason: 'Should be testing a file of size 4MB + 100 bytes');
    });

    test('Should provide meaningful error messages for failures', () async {
      // This test validates that if a test were to fail,
      // it would provide useful error information

      // Arrange
      final runner = await RegressionTestRunner.createFromAssets('droid-photos-v1');
      expect(runner, isNotNull);

      // Act
      final results = await runner!.runTests();

      // Assert - All tests should pass, but check error field structure
      for (final result in results) {
        if (!result.passed) {
          expect(result.error, isNotNull,
            reason: 'Failed tests should have error messages');
          expect(result.error, isNotEmpty,
            reason: 'Error messages should not be empty');
        } else {
          expect(result.error, isNull,
            reason: 'Passed tests should not have error messages');
        }

        // All results should have required fields
        expect(result.testId, isNotEmpty);
        expect(result.description, isNotEmpty);
        expect(result.algorithm, isNotEmpty);
      }
    });

    test('Should handle empty files correctly', () async {
      // Arrange
      final runner = await RegressionTestRunner.createFromAssets('droid-photos-v1');
      expect(runner, isNotNull);

      // Act
      final results = await runner!.runTests();

      // Assert - Find empty file test
      final emptyFileTest = results.firstWhere(
        (r) => r.testId == 'file_empty_droid',
        orElse: () => throw TestFailure('Empty file test not found'),
      );

      expect(emptyFileTest.passed, isTrue,
        reason: 'Should correctly handle empty file encryption/decryption');
      expect(emptyFileTest.description, contains('0 bytes'),
        reason: 'Should indicate this is a 0-byte file test');
    });
  });

  group('Cross-Platform Compatibility Tests', () {
    test('Test data should follow specification format', () async {
      // Arrange
      final runner = await RegressionTestRunner.createFromAssets('droid-photos-v1');
      expect(runner, isNotNull);

      // Assert - Validate test vector structure
      expect(runner!.testVectors['version'], equals('1.0.0'));
      expect(runner.testVectors['platform'], equals('droid'));
      expect(runner.testVectors['test_suites'], isList);

      // Check test suite structure
      final testSuites = runner.testVectors['test_suites'] as List;
      for (final suite in testSuites) {
        expect(suite['algorithm'], isNotNull);
        expect(suite['description'], isNotNull);
        expect(suite['vectors'], isList);

        final vectors = suite['vectors'] as List;
        for (final vector in vectors) {
          expect(vector['id'], isNotNull);
          expect(vector['description'], isNotNull);
          expect(vector['inputs'], isMap);
          expect(vector['outputs'], isMap);
        }
      }
    });

    test('File vectors should include all required metadata', () async {
      // Arrange
      final runner = await RegressionTestRunner.createFromAssets('droid-photos-v1');
      expect(runner, isNotNull);

      // Assert - Validate file vectors if present
      if (runner!.fileVectors != null) {
        expect(runner.fileVectors!['version'], equals('1.0.0'));
        expect(runner.fileVectors!['algorithm'], equals('XChaCha20-Poly1305-Stream'));
        expect(runner.fileVectors!['files'], isList);

        final files = runner.fileVectors!['files'] as List;
        for (final file in files) {
          expect(file['id'], isNotNull);
          expect(file['filename'], isNotNull);
          expect(file['original_size'], isNotNull);
          expect(file['encrypted_size'], isNotNull);
          expect(file['key_hex'], isNotNull);
          expect(file['header_hex'], isNotNull);
          expect(file['original_sha256'], isNotNull);
          expect(file['encrypted_sha256'], isNotNull);
          expect(file['chunk_size'], equals(4194304),
            reason: 'Chunk size should be 4MB');
        }
      }
    });
  });
}