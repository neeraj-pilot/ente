import 'dart:typed_data';

import 'package:ente_crypto/ente_crypto.dart';
import 'package:logging/logging.dart';

import 'package:ente_crypto_example/screens/test_runner_screen.dart';

class KeyDerivationTests extends TestRunner {
  final _logger = Logger('KeyDerivationTests');

  @override
  Future<TestResults> runTests() async {
    final results = TestResults();

    results.addResult(await _testSensitiveKeyDerivation());
    results.addResult(await _testInteractiveKeyDerivation());
    results.addResult(await _testLoginKeyDerivation());
    results.addResult(await _testSaltGeneration());
    results.addResult(await _testKeyDerivationDeterminism());
    results.addResult(await _testKeyDerivationUniquenessSalt());
    results.addResult(await _testKeyDerivationUniquenessPassword());
    results.addResult(await _testEmptyPasswordDerivation());
    results.addResult(await _testLongPasswordDerivation());
    results.addResult(await _testMemoryLimitHandling());

    return results;
  }

  Future<TestResult> _testSensitiveKeyDerivation() async {
    final stopwatch = Stopwatch()..start();
    try {
      final password = Uint8List.fromList('MySecurePassword123!'.codeUnits);
      final salt = CryptoUtil.getSaltToDeriveKey();

      final result = await CryptoUtil.deriveSensitiveKey(password, salt);

      if (result.key.length != 32) {
        throw Exception('Derived key has wrong length: ${result.key.length}');
      }

      if (result.memLimit <= 0 || result.opsLimit <= 0) {
        throw Exception(
          'Invalid params: memLimit=${result.memLimit}, opsLimit=${result.opsLimit}',
        );
      }

      _logger.info(
        'Sensitive key derivation succeeded with memLimit=${result.memLimit}, opsLimit=${result.opsLimit}',
      );

      return TestResult(
        name: 'Sensitive Key Derivation',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Sensitive key derivation test failed', e);
      return TestResult(
        name: 'Sensitive Key Derivation',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testInteractiveKeyDerivation() async {
    final stopwatch = Stopwatch()..start();
    try {
      final password = Uint8List.fromList('QuickPassword'.codeUnits);
      final salt = CryptoUtil.getSaltToDeriveKey();

      final result = await CryptoUtil.deriveInteractiveKey(password, salt);

      if (result.key.length != 32) {
        throw Exception('Derived key has wrong length: ${result.key.length}');
      }

      final expectedMemLimit = 67108864;
      final expectedOpsLimit = 2;

      if (result.memLimit != expectedMemLimit ||
          result.opsLimit != expectedOpsLimit) {
        throw Exception(
          'Unexpected params: memLimit=${result.memLimit} (expected $expectedMemLimit), '
          'opsLimit=${result.opsLimit} (expected $expectedOpsLimit)',
        );
      }

      _logger.info('Interactive key derivation test passed');
      return TestResult(
        name: 'Interactive Key Derivation',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Interactive key derivation test failed', e);
      return TestResult(
        name: 'Interactive Key Derivation',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testLoginKeyDerivation() async {
    final stopwatch = Stopwatch()..start();
    try {
      final masterKey = CryptoUtil.generateKey();

      final loginKey = await CryptoUtil.deriveLoginKey(masterKey);

      if (loginKey.length != 16) {
        throw Exception('Login key has wrong length: ${loginKey.length}');
      }

      final loginKey2 = await CryptoUtil.deriveLoginKey(masterKey);

      if (!_areListsEqual(loginKey, loginKey2)) {
        throw Exception('Login key derivation is not deterministic');
      }

      _logger.info('Login key derivation test passed');
      return TestResult(
        name: 'Login Key Derivation',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Login key derivation test failed', e);
      return TestResult(
        name: 'Login Key Derivation',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testSaltGeneration() async {
    final stopwatch = Stopwatch()..start();
    try {
      final salt1 = CryptoUtil.getSaltToDeriveKey();
      final salt2 = CryptoUtil.getSaltToDeriveKey();

      if (salt1.length != 16) {
        throw Exception('Salt has wrong length: ${salt1.length}');
      }

      if (_areListsEqual(salt1, salt2)) {
        throw Exception('Generated salts are not unique');
      }

      _logger.info('Salt generation test passed');
      return TestResult(
        name: 'Salt Generation',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Salt generation test failed', e);
      return TestResult(
        name: 'Salt Generation',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testKeyDerivationDeterminism() async {
    final stopwatch = Stopwatch()..start();
    try {
      final password = Uint8List.fromList('TestPassword'.codeUnits);
      final salt = CryptoUtil.getSaltToDeriveKey();

      final result1 = await CryptoUtil.deriveInteractiveKey(password, salt);
      final result2 = await CryptoUtil.deriveInteractiveKey(password, salt);

      if (!_areListsEqual(result1.key, result2.key)) {
        throw Exception('Key derivation is not deterministic');
      }

      _logger.info('Key derivation determinism test passed');
      return TestResult(
        name: 'Key Derivation Determinism',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Key derivation determinism test failed', e);
      return TestResult(
        name: 'Key Derivation Determinism',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testKeyDerivationUniquenessSalt() async {
    final stopwatch = Stopwatch()..start();
    try {
      final password = Uint8List.fromList('SamePassword'.codeUnits);
      final salt1 = CryptoUtil.getSaltToDeriveKey();
      final salt2 = CryptoUtil.getSaltToDeriveKey();

      final result1 = await CryptoUtil.deriveInteractiveKey(password, salt1);
      final result2 = await CryptoUtil.deriveInteractiveKey(password, salt2);

      if (_areListsEqual(result1.key, result2.key)) {
        throw Exception('Different salts produced same key');
      }

      _logger.info('Key uniqueness with different salts test passed');
      return TestResult(
        name: 'Key Uniqueness (Different Salts)',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Key uniqueness salt test failed', e);
      return TestResult(
        name: 'Key Uniqueness (Different Salts)',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testKeyDerivationUniquenessPassword() async {
    final stopwatch = Stopwatch()..start();
    try {
      final password1 = Uint8List.fromList('Password1'.codeUnits);
      final password2 = Uint8List.fromList('Password2'.codeUnits);
      final salt = CryptoUtil.getSaltToDeriveKey();

      final result1 = await CryptoUtil.deriveInteractiveKey(password1, salt);
      final result2 = await CryptoUtil.deriveInteractiveKey(password2, salt);

      if (_areListsEqual(result1.key, result2.key)) {
        throw Exception('Different passwords produced same key');
      }

      _logger.info('Key uniqueness with different passwords test passed');
      return TestResult(
        name: 'Key Uniqueness (Different Passwords)',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Key uniqueness password test failed', e);
      return TestResult(
        name: 'Key Uniqueness (Different Passwords)',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testEmptyPasswordDerivation() async {
    final stopwatch = Stopwatch()..start();
    try {
      final password = Uint8List(0);
      final salt = CryptoUtil.getSaltToDeriveKey();

      final result = await CryptoUtil.deriveInteractiveKey(password, salt);

      if (result.key.length != 32) {
        throw Exception('Derived key from empty password has wrong length');
      }

      _logger.info('Empty password derivation test passed');
      return TestResult(
        name: 'Empty Password Derivation',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Empty password derivation test failed', e);
      return TestResult(
        name: 'Empty Password Derivation',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testLongPasswordDerivation() async {
    final stopwatch = Stopwatch()..start();
    try {
      final longPassword = 'A' * 1000;
      final password = Uint8List.fromList(longPassword.codeUnits);
      final salt = CryptoUtil.getSaltToDeriveKey();

      final result = await CryptoUtil.deriveInteractiveKey(password, salt);

      if (result.key.length != 32) {
        throw Exception('Derived key from long password has wrong length');
      }

      _logger.info('Long password derivation test passed');
      return TestResult(
        name: 'Long Password (1000 chars)',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Long password derivation test failed', e);
      return TestResult(
        name: 'Long Password (1000 chars)',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testMemoryLimitHandling() async {
    final stopwatch = Stopwatch()..start();
    try {
      final password = Uint8List.fromList('TestMemoryLimits'.codeUnits);
      final salt = CryptoUtil.getSaltToDeriveKey();

      final result = await CryptoUtil.deriveSensitiveKey(password, salt);

      final expectedProduct = 1073741824 * 4;
      final actualProduct = result.memLimit * result.opsLimit;

      if ((actualProduct - expectedProduct).abs() > expectedProduct * 0.01) {
        throw Exception(
          'Memory/Ops product is not preserved: expected=$expectedProduct, actual=$actualProduct',
        );
      }

      _logger.info(
        'Memory limit handling test passed (memLimit=${result.memLimit}, opsLimit=${result.opsLimit})',
      );
      return TestResult(
        name: 'Memory Limit Handling',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Memory limit handling test failed', e);
      return TestResult(
        name: 'Memory Limit Handling',
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
