import 'dart:typed_data';

import 'package:ente_crypto/ente_crypto.dart';
import 'package:logging/logging.dart';

import 'package:ente_crypto_example/screens/test_runner_screen.dart';

class BasicCryptoTests extends TestRunner {
  final _logger = Logger('BasicCryptoTests');

  @override
  Future<TestResults> runTests() async {
    final results = TestResults();

    results.addResult(await _testSmallDataEncryption());
    results.addResult(await _testLargeDataEncryption());
    results.addResult(await _testEmptyDataEncryption());
    results.addResult(await _testInvalidKeyDecryption());
    results.addResult(await _testInvalidNonceDecryption());
    results.addResult(await _testBase64Encoding());
    results.addResult(await _testHexEncoding());
    results.addResult(await _testKeyGeneration());
    results.addResult(await _testSealedBox());
    results.addResult(await _testChaChaSmallData());

    return results;
  }

  Future<TestResult> _testSmallDataEncryption() async {
    final stopwatch = Stopwatch()..start();
    try {
      final testData = Uint8List.fromList('Hello, World!'.codeUnits);
      final key = CryptoUtil.generateKey();

      final encryptionResult = CryptoUtil.encryptSync(testData, key);

      final decryptedData = await CryptoUtil.decrypt(
        encryptionResult.encryptedData!,
        encryptionResult.key!,
        encryptionResult.nonce!,
      );

      if (!_areListsEqual(testData, decryptedData)) {
        throw Exception('Decrypted data does not match original');
      }

      _logger.info('Small data encryption test passed');
      return TestResult(
        name: 'Small Data Encryption',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Small data encryption test failed', e);
      return TestResult(
        name: 'Small Data Encryption',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testLargeDataEncryption() async {
    final stopwatch = Stopwatch()..start();
    try {
      final testData = Uint8List(1024 * 1024);
      for (int i = 0; i < testData.length; i++) {
        testData[i] = i % 256;
      }
      final key = CryptoUtil.generateKey();

      final encryptionResult = CryptoUtil.encryptSync(testData, key);

      final decryptedData = await CryptoUtil.decrypt(
        encryptionResult.encryptedData!,
        encryptionResult.key!,
        encryptionResult.nonce!,
      );

      if (!_areListsEqual(testData, decryptedData)) {
        throw Exception('Decrypted data does not match original');
      }

      _logger.info('Large data encryption test passed');
      return TestResult(
        name: 'Large Data Encryption (1MB)',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Large data encryption test failed', e);
      return TestResult(
        name: 'Large Data Encryption (1MB)',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testEmptyDataEncryption() async {
    final stopwatch = Stopwatch()..start();
    try {
      final testData = Uint8List(0);
      final key = CryptoUtil.generateKey();

      final encryptionResult = CryptoUtil.encryptSync(testData, key);

      final decryptedData = await CryptoUtil.decrypt(
        encryptionResult.encryptedData!,
        encryptionResult.key!,
        encryptionResult.nonce!,
      );

      if (decryptedData.length != 0) {
        throw Exception('Decrypted empty data is not empty');
      }

      _logger.info('Empty data encryption test passed');
      return TestResult(
        name: 'Empty Data Encryption',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Empty data encryption test failed', e);
      return TestResult(
        name: 'Empty Data Encryption',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testInvalidKeyDecryption() async {
    final stopwatch = Stopwatch()..start();
    try {
      final testData = Uint8List.fromList('Secret Message'.codeUnits);
      final key = CryptoUtil.generateKey();
      final wrongKey = CryptoUtil.generateKey();

      final encryptionResult = CryptoUtil.encryptSync(testData, key);

      try {
        await CryptoUtil.decrypt(
          encryptionResult.encryptedData!,
          wrongKey,
          encryptionResult.nonce!,
        );
        throw Exception('Should have failed with wrong key');
      } catch (e) {
        _logger.info('Correctly rejected invalid key: $e');
      }

      _logger.info('Invalid key decryption test passed');
      return TestResult(
        name: 'Invalid Key Decryption',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Invalid key decryption test failed', e);
      return TestResult(
        name: 'Invalid Key Decryption',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testInvalidNonceDecryption() async {
    final stopwatch = Stopwatch()..start();
    try {
      final testData = Uint8List.fromList('Secret Message'.codeUnits);
      final key = CryptoUtil.generateKey();

      final encryptionResult = CryptoUtil.encryptSync(testData, key);

      final wrongNonce = Uint8List(24);
      for (int i = 0; i < wrongNonce.length; i++) {
        wrongNonce[i] = 255;
      }

      try {
        await CryptoUtil.decrypt(
          encryptionResult.encryptedData!,
          encryptionResult.key!,
          wrongNonce,
        );
        throw Exception('Should have failed with wrong nonce');
      } catch (e) {
        _logger.info('Correctly rejected invalid nonce: $e');
      }

      _logger.info('Invalid nonce decryption test passed');
      return TestResult(
        name: 'Invalid Nonce Decryption',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Invalid nonce decryption test failed', e);
      return TestResult(
        name: 'Invalid Nonce Decryption',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testBase64Encoding() async {
    final stopwatch = Stopwatch()..start();
    try {
      final testData = Uint8List.fromList('Test Base64 Encoding!'.codeUnits);

      final encoded = CryptoUtil.bin2base64(testData);
      final decoded = CryptoUtil.base642bin(encoded);

      if (!_areListsEqual(testData, decoded)) {
        throw Exception('Base64 round-trip failed');
      }

      final urlSafeEncoded = CryptoUtil.bin2base64(testData, urlSafe: true);
      final urlSafeDecoded = CryptoUtil.base642bin(urlSafeEncoded);

      if (!_areListsEqual(testData, urlSafeDecoded)) {
        throw Exception('URL-safe Base64 round-trip failed');
      }

      _logger.info('Base64 encoding test passed');
      return TestResult(
        name: 'Base64 Encoding',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Base64 encoding test failed', e);
      return TestResult(
        name: 'Base64 Encoding',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testHexEncoding() async {
    final stopwatch = Stopwatch()..start();
    try {
      final testData = Uint8List.fromList([0, 15, 255, 128, 64, 32, 16, 8]);

      final encoded = CryptoUtil.bin2hex(testData);
      final decoded = CryptoUtil.hex2bin(encoded);

      if (!_areListsEqual(testData, decoded)) {
        throw Exception('Hex round-trip failed');
      }

      _logger.info('Hex encoding test passed');
      return TestResult(
        name: 'Hex Encoding',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Hex encoding test failed', e);
      return TestResult(
        name: 'Hex Encoding',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testKeyGeneration() async {
    final stopwatch = Stopwatch()..start();
    try {
      final key1 = CryptoUtil.generateKey();
      final key2 = CryptoUtil.generateKey();

      if (key1.length != 32) {
        throw Exception('Generated key has wrong length: ${key1.length}');
      }

      if (_areListsEqual(key1, key2)) {
        throw Exception('Generated keys are not unique');
      }

      _logger.info('Key generation test passed');
      return TestResult(
        name: 'Key Generation',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Key generation test failed', e);
      return TestResult(
        name: 'Key Generation',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testSealedBox() async {
    final stopwatch = Stopwatch()..start();
    try {
      final testData = Uint8List.fromList('Sealed box message'.codeUnits);
      final keyPair = await CryptoUtil.generateKeyPair();

      final sealed = CryptoUtil.sealSync(testData, keyPair.pk);

      final opened = CryptoUtil.openSealSync(
        sealed,
        keyPair.pk,
        keyPair.sk,
      );

      if (!_areListsEqual(testData, opened)) {
        throw Exception('Sealed box round-trip failed');
      }

      _logger.info('Sealed box test passed');
      return TestResult(
        name: 'Sealed Box Encryption',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Sealed box test failed', e);
      return TestResult(
        name: 'Sealed Box Encryption',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testChaChaSmallData() async {
    final stopwatch = Stopwatch()..start();
    try {
      final testData = Uint8List.fromList('ChaCha20 test data!'.codeUnits);
      final key = CryptoUtil.generateKey();

      final encryptionResult = await CryptoUtil.encryptChaCha(testData, key);

      final decryptedData = await CryptoUtil.decryptChaCha(
        encryptionResult.encryptedData!,
        key,
        encryptionResult.header!,
      );

      if (!_areListsEqual(testData, decryptedData)) {
        throw Exception('ChaCha decrypted data does not match original');
      }

      _logger.info('ChaCha small data test passed');
      return TestResult(
        name: 'ChaCha20 Small Data',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('ChaCha small data test failed', e);
      return TestResult(
        name: 'ChaCha20 Small Data',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  bool _areListsEqual(List<int> list1, List<int> list2) {
    if (list1.length != list2.length) return false;
    for (int i = 0; i < list1.length; i++) {
      if (list1[i] != list2[i]) return false;
    }
    return true;
  }
}
