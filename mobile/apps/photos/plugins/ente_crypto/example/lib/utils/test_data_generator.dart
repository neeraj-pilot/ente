import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:ente_crypto/ente_crypto.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';

class TestDataGenerator {
  static final _logger = Logger('TestDataGenerator');
  static final _random = Random();

  static Future<TestDataSet> generateCompleteTestDataSet() async {
    final testDataSet = TestDataSet(
      version: '1.0.0',
      timestamp: DateTime.now().toIso8601String(),
      testVectors: [],
    );

    testDataSet.testVectors.addAll(await _generateXSalsa20Vectors());
    testDataSet.testVectors.addAll(await _generateChaCha20Vectors());
    testDataSet.testVectors.addAll(await _generateArgon2idVectors());
    testDataSet.testVectors.addAll(await _generateSealedBoxVectors());
    testDataSet.testVectors.addAll(await _generateFileVectors());

    return testDataSet;
  }

  static Future<List<TestVector>> _generateXSalsa20Vectors() async {
    final vectors = <TestVector>[];

    for (final size in [0, 1, 16, 256, 1024, 4096]) {
      final plaintext = _generateRandomBytes(size);
      final key = CryptoUtil.generateKey();

      final encryptionResult = CryptoUtil.encryptSync(plaintext, key);

      vectors.add(TestVector(
        algorithm: 'XSalsa20-Poly1305',
        description: 'Size: $size bytes',
        inputs: {
          'plaintext': base64Encode(plaintext),
          'key': base64Encode(key),
          'nonce': base64Encode(encryptionResult.nonce!),
        },
        outputs: {
          'ciphertext': base64Encode(encryptionResult.encryptedData!),
        },
        metadata: {
          'size': size.toString(),
          'timestamp': DateTime.now().toIso8601String(),
        },
      ));
    }

    _logger.info('Generated ${vectors.length} XSalsa20 test vectors');
    return vectors;
  }

  static Future<List<TestVector>> _generateChaCha20Vectors() async {
    final vectors = <TestVector>[];

    for (final size in [0, 1, 100, 1000, 10000]) {
      final plaintext = _generateRandomBytes(size);
      final key = CryptoUtil.generateKey();

      final encryptionResult = await CryptoUtil.encryptChaCha(plaintext, key);

      vectors.add(TestVector(
        algorithm: 'XChaCha20-Poly1305',
        description: 'Size: $size bytes',
        inputs: {
          'plaintext': base64Encode(plaintext),
          'key': base64Encode(key),
          'header': base64Encode(encryptionResult.header!),
        },
        outputs: {
          'ciphertext': base64Encode(encryptionResult.encryptedData!),
        },
        metadata: {
          'size': size.toString(),
          'timestamp': DateTime.now().toIso8601String(),
        },
      ));
    }

    _logger.info('Generated ${vectors.length} ChaCha20 test vectors');
    return vectors;
  }

  static Future<List<TestVector>> _generateArgon2idVectors() async {
    final vectors = <TestVector>[];

    final testPasswords = [
      '',
      'a',
      'password',
      'MySecurePassword123!',
      'A' * 100,
      'Special!@#\$%^&*()_+{}|:"<>?[]\\;\',./',
    ];

    for (final password in testPasswords) {
      final passwordBytes = Uint8List.fromList(password.codeUnits);
      final salt = CryptoUtil.getSaltToDeriveKey();

      final interactiveResult =
          await CryptoUtil.deriveInteractiveKey(passwordBytes, salt);

      vectors.add(TestVector(
        algorithm: 'Argon2id-Interactive',
        description: 'Password length: ${password.length}',
        inputs: {
          'password': base64Encode(passwordBytes),
          'salt': base64Encode(salt),
        },
        outputs: {
          'key': base64Encode(interactiveResult.key),
        },
        metadata: {
          'memLimit': interactiveResult.memLimit.toString(),
          'opsLimit': interactiveResult.opsLimit.toString(),
          'passwordLength': password.length.toString(),
          'timestamp': DateTime.now().toIso8601String(),
        },
      ));

      try {
        final sensitiveResult =
            await CryptoUtil.deriveSensitiveKey(passwordBytes, salt);

        vectors.add(TestVector(
          algorithm: 'Argon2id-Sensitive',
          description: 'Password length: ${password.length}',
          inputs: {
            'password': base64Encode(passwordBytes),
            'salt': base64Encode(salt),
          },
          outputs: {
            'key': base64Encode(sensitiveResult.key),
          },
          metadata: {
            'memLimit': sensitiveResult.memLimit.toString(),
            'opsLimit': sensitiveResult.opsLimit.toString(),
            'passwordLength': password.length.toString(),
            'timestamp': DateTime.now().toIso8601String(),
          },
        ));
      } catch (e) {
        _logger.warning(
            'Sensitive derivation failed for password length ${password.length}');
      }
    }

    _logger.info('Generated ${vectors.length} Argon2id test vectors');
    return vectors;
  }

  static Future<List<TestVector>> _generateSealedBoxVectors() async {
    final vectors = <TestVector>[];

    for (final size in [0, 1, 32, 128, 512]) {
      final plaintext = _generateRandomBytes(size);
      final keyPair = await CryptoUtil.generateKeyPair();

      final sealed = CryptoUtil.sealSync(plaintext, keyPair.pk);

      vectors.add(TestVector(
        algorithm: 'SealedBox',
        description: 'Size: $size bytes',
        inputs: {
          'plaintext': base64Encode(plaintext),
          'publicKey': base64Encode(keyPair.pk),
          'secretKey': base64Encode(keyPair.sk),
        },
        outputs: {
          'sealed': base64Encode(sealed),
        },
        metadata: {
          'size': size.toString(),
          'timestamp': DateTime.now().toIso8601String(),
        },
      ));
    }

    _logger.info('Generated ${vectors.length} SealedBox test vectors');
    return vectors;
  }

  static Future<List<TestVector>> _generateFileVectors() async {
    final vectors = <TestVector>[];
    final tempDir = await getTemporaryDirectory();

    final fileSizes = [
      0,
      1,
      4 * 1024 * 1024 - 1,
      4 * 1024 * 1024,
      4 * 1024 * 1024 + 1,
      8 * 1024 * 1024,
    ];

    for (final size in fileSizes) {
      final sourceFile = File('${tempDir.path}/test_vector_source_$size.dat');
      final encryptedFile =
          File('${tempDir.path}/test_vector_encrypted_$size.dat');

      final testData = _generateRandomBytes(size);
      await sourceFile.writeAsBytes(testData);

      final key = CryptoUtil.generateKey();
      final encryptResult = await CryptoUtil.encryptFile(
        sourceFile.path,
        encryptedFile.path,
        key: key,
      );

      final encryptedData = await encryptedFile.readAsBytes();
      final hash = await CryptoUtil.getHash(sourceFile);

      vectors.add(TestVector(
        algorithm: 'XChaCha20-Poly1305-Stream',
        description: 'File size: $size bytes',
        inputs: {
          'plaintext': base64Encode(testData),
          'key': base64Encode(key),
          'header': base64Encode(encryptResult.header!),
        },
        outputs: {
          'ciphertext': base64Encode(encryptedData),
          'hash': base64Encode(hash),
        },
        metadata: {
          'size': size.toString(),
          'chunks': (size ~/ (4 * 1024 * 1024) + 1).toString(),
          'timestamp': DateTime.now().toIso8601String(),
        },
      ));

      await sourceFile.delete();
      await encryptedFile.delete();
    }

    _logger.info('Generated ${vectors.length} file encryption test vectors');
    return vectors;
  }

  static Future<void> saveTestDataSet(TestDataSet dataSet) async {
    final appDir = await getApplicationDocumentsDirectory();
    final testDataDir =
        Directory('${appDir.path}/test_data/regression/${dataSet.version}');
    await testDataDir.create(recursive: true);

    final file = File('${testDataDir.path}/test_vectors.json');
    final jsonData = dataSet.toJson();
    await file.writeAsString(jsonEncode(jsonData));

    _logger.info('Saved test data set to ${file.path}');
  }

  static Future<TestDataSet?> loadTestDataSet(String version) async {
    try {
      final appDir = await getApplicationDocumentsDirectory();
      final file = File(
          '${appDir.path}/test_data/regression/$version/test_vectors.json');

      if (!await file.exists()) {
        _logger.warning('Test data file not found: ${file.path}');
        return null;
      }

      final jsonString = await file.readAsString();
      final jsonData = jsonDecode(jsonString);
      return TestDataSet.fromJson(jsonData);
    } catch (e) {
      _logger.severe('Failed to load test data set', e);
      return null;
    }
  }

  static Future<RegressionTestReport> runRegressionTests(
      TestDataSet dataSet) async {
    final report = RegressionTestReport(
      version: dataSet.version,
      timestamp: DateTime.now().toIso8601String(),
      results: [],
    );

    for (final vector in dataSet.testVectors) {
      final result = await _verifyTestVector(vector);
      report.results.add(result);
    }

    final passedCount = report.results.where((r) => r.passed).length;
    final totalCount = report.results.length;

    _logger.info('Regression test results: $passedCount/$totalCount passed');

    return report;
  }

  static Future<RegressionTestResult> _verifyTestVector(
      TestVector vector) async {
    try {
      switch (vector.algorithm) {
        case 'XSalsa20-Poly1305':
          return await _verifyXSalsa20Vector(vector);
        case 'XChaCha20-Poly1305':
          return await _verifyChaCha20Vector(vector);
        case 'Argon2id-Interactive':
        case 'Argon2id-Sensitive':
          return await _verifyArgon2idVector(vector);
        case 'SealedBox':
          return await _verifySealedBoxVector(vector);
        case 'XChaCha20-Poly1305-Stream':
          return await _verifyFileVector(vector);
        default:
          return RegressionTestResult(
            testName: vector.description,
            algorithm: vector.algorithm,
            passed: false,
            error: 'Unknown algorithm: ${vector.algorithm}',
          );
      }
    } catch (e) {
      return RegressionTestResult(
        testName: vector.description,
        algorithm: vector.algorithm,
        passed: false,
        error: e.toString(),
      );
    }
  }

  static Future<RegressionTestResult> _verifyXSalsa20Vector(
      TestVector vector) async {
    final plaintext = base64Decode(vector.inputs['plaintext']!);
    final key = base64Decode(vector.inputs['key']!);
    final nonce = base64Decode(vector.inputs['nonce']!);
    final expectedCiphertext = base64Decode(vector.outputs['ciphertext']!);

    final encryptedData = CryptoUtil.encryptSync(plaintext, key);
    final decryptedData =
        await CryptoUtil.decrypt(expectedCiphertext, key, nonce);

    final passed = _areListsEqual(decryptedData, plaintext);

    return RegressionTestResult(
      testName: vector.description,
      algorithm: vector.algorithm,
      passed: passed,
      error: passed ? null : 'Decryption mismatch',
    );
  }

  static Future<RegressionTestResult> _verifyChaCha20Vector(
      TestVector vector) async {
    final plaintext = base64Decode(vector.inputs['plaintext']!);
    final key = base64Decode(vector.inputs['key']!);
    final header = base64Decode(vector.inputs['header']!);
    final expectedCiphertext = base64Decode(vector.outputs['ciphertext']!);

    final decryptedData =
        await CryptoUtil.decryptChaCha(expectedCiphertext, key, header);

    final passed = _areListsEqual(decryptedData, plaintext);

    return RegressionTestResult(
      testName: vector.description,
      algorithm: vector.algorithm,
      passed: passed,
      error: passed ? null : 'Decryption mismatch',
    );
  }

  static Future<RegressionTestResult> _verifyArgon2idVector(
      TestVector vector) async {
    final password = base64Decode(vector.inputs['password']!);
    final salt = base64Decode(vector.inputs['salt']!);
    final expectedKey = base64Decode(vector.outputs['key']!);
    final memLimit = int.parse(vector.metadata!['memLimit']!);
    final opsLimit = int.parse(vector.metadata!['opsLimit']!);

    final derivedKey =
        await CryptoUtil.deriveKey(password, salt, memLimit, opsLimit);

    final passed = _areListsEqual(derivedKey, expectedKey);

    return RegressionTestResult(
      testName: vector.description,
      algorithm: vector.algorithm,
      passed: passed,
      error: passed ? null : 'Key derivation mismatch',
    );
  }

  static Future<RegressionTestResult> _verifySealedBoxVector(
      TestVector vector) async {
    final plaintext = base64Decode(vector.inputs['plaintext']!);
    final publicKey = base64Decode(vector.inputs['publicKey']!);
    final secretKey = base64Decode(vector.inputs['secretKey']!);
    final sealed = base64Decode(vector.outputs['sealed']!);

    final opened = CryptoUtil.openSealSync(sealed, publicKey, secretKey);

    final passed = _areListsEqual(opened, plaintext);

    return RegressionTestResult(
      testName: vector.description,
      algorithm: vector.algorithm,
      passed: passed,
      error: passed ? null : 'Sealed box decryption mismatch',
    );
  }

  static Future<RegressionTestResult> _verifyFileVector(
      TestVector vector) async {
    final tempDir = await getTemporaryDirectory();
    final sourceFile = File('${tempDir.path}/verify_source.dat');
    final encryptedFile = File('${tempDir.path}/verify_encrypted.dat');
    final decryptedFile = File('${tempDir.path}/verify_decrypted.dat');

    try {
      final plaintext = base64Decode(vector.inputs['plaintext']!);
      final key = base64Decode(vector.inputs['key']!);
      final header = base64Decode(vector.inputs['header']!);
      final ciphertext = base64Decode(vector.outputs['ciphertext']!);

      await encryptedFile.writeAsBytes(ciphertext);

      await CryptoUtil.decryptFile(
        encryptedFile.path,
        decryptedFile.path,
        header,
        key,
      );

      final decryptedData = await decryptedFile.readAsBytes();

      final passed = _areListsEqual(decryptedData, plaintext);

      return RegressionTestResult(
        testName: vector.description,
        algorithm: vector.algorithm,
        passed: passed,
        error: passed ? null : 'File decryption mismatch',
      );
    } finally {
      if (await sourceFile.exists()) await sourceFile.delete();
      if (await encryptedFile.exists()) await encryptedFile.delete();
      if (await decryptedFile.exists()) await decryptedFile.delete();
    }
  }

  static Uint8List _generateRandomBytes(int size) {
    final bytes = Uint8List(size);
    for (int i = 0; i < size; i++) {
      bytes[i] = _random.nextInt(256);
    }
    return bytes;
  }

  static bool _areListsEqual(List<int> list1, List<int> list2) {
    if (list1.length != list2.length) return false;
    for (int i = 0; i < list1.length; i++) {
      if (list1[i] != list2[i]) return false;
    }
    return true;
  }
}

class TestDataSet {
  final String version;
  final String timestamp;
  final List<TestVector> testVectors;

  TestDataSet({
    required this.version,
    required this.timestamp,
    required this.testVectors,
  });

  Map<String, dynamic> toJson() => {
        'version': version,
        'timestamp': timestamp,
        'testVectors': testVectors.map((v) => v.toJson()).toList(),
      };

  factory TestDataSet.fromJson(Map<String, dynamic> json) => TestDataSet(
        version: json['version'],
        timestamp: json['timestamp'],
        testVectors: (json['testVectors'] as List)
            .map((v) => TestVector.fromJson(v))
            .toList(),
      );
}

class TestVector {
  final String algorithm;
  final String description;
  final Map<String, String> inputs;
  final Map<String, String> outputs;
  final Map<String, String>? metadata;

  TestVector({
    required this.algorithm,
    required this.description,
    required this.inputs,
    required this.outputs,
    this.metadata,
  });

  Map<String, dynamic> toJson() => {
        'algorithm': algorithm,
        'description': description,
        'inputs': inputs,
        'outputs': outputs,
        if (metadata != null) 'metadata': metadata,
      };

  factory TestVector.fromJson(Map<String, dynamic> json) => TestVector(
        algorithm: json['algorithm'],
        description: json['description'],
        inputs: Map<String, String>.from(json['inputs']),
        outputs: Map<String, String>.from(json['outputs']),
        metadata: json['metadata'] != null
            ? Map<String, String>.from(json['metadata'])
            : null,
      );
}

class RegressionTestReport {
  final String version;
  final String timestamp;
  final List<RegressionTestResult> results;

  RegressionTestReport({
    required this.version,
    required this.timestamp,
    required this.results,
  });

  int get passedCount => results.where((r) => r.passed).length;
  int get failedCount => results.where((r) => !r.passed).length;
  int get totalCount => results.length;
}

class RegressionTestResult {
  final String testName;
  final String algorithm;
  final bool passed;
  final String? error;

  RegressionTestResult({
    required this.testName,
    required this.algorithm,
    required this.passed,
    this.error,
  });
}
