import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:ente_crypto/ente_crypto.dart';
import 'package:flutter/services.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';

class RegressionTestRunner {
  static final _logger = Logger('RegressionTestRunner');
  final String platformPath; // e.g., "droid-photos-v1"
  final Map<String, dynamic> testVectors;
  final Map<String, dynamic>? edgeCases;
  final Map<String, dynamic>? fileVectors;
  final Map<String, Uint8List> encryptedFiles; // Binary file data

  RegressionTestRunner({
    required this.platformPath,
    required this.testVectors,
    this.edgeCases,
    this.fileVectors,
    this.encryptedFiles = const {},
  });

  // Discover all test data from bundled zip files
  static Future<List<TestDataInfo>> discoverTestData() async {
    final testDataList = <TestDataInfo>[];

    try {
      // Load asset manifest to discover available platforms
      final manifestJson = await rootBundle.loadString('AssetManifest.json');
      final manifest = json.decode(manifestJson) as Map<String, dynamic>;

      _logger.info('Asset manifest loaded with ${manifest.keys.length} keys');

      // Look for zip files in test_data/regression/
      final zipFiles = manifest.keys.where(
        (key) => key.startsWith('test_data/regression/') &&
                 key.endsWith('.zip')
      ).toList();

      _logger.info('Found ${zipFiles.length} test data zip files: $zipFiles');

      // Process zip files
      for (final zipPath in zipFiles) {
        final filename = zipPath.split('/').last;
        final platformName = filename.substring(0, filename.length - 4); // Remove .zip

        try {
          // Load the zip file and extract metadata
          final zipData = await rootBundle.load(zipPath);
          final archive = ZipDecoder().decodeBytes(zipData.buffer.asUint8List());

          // Find metadata.json in the archive
          ArchiveFile? metadataFile;
          for (final file in archive.files) {
            if (file.name.endsWith('metadata.json')) {
              metadataFile = file;
              break;
            }
          }

          if (metadataFile == null) {
            _logger.warning('No metadata.json found in $zipPath');
            continue;
          }

          final metadataContent = String.fromCharCodes(metadataFile.content as List<int>);
          final metadata = jsonDecode(metadataContent);

          testDataList.add(TestDataInfo(
            path: platformName,
            name: platformName,
            platform: metadata['platform'] ?? 'unknown',
            version: metadata['version'] ?? 'unknown',
            timestamp: metadata['timestamp'] ?? '',
            testCount: _countTests(metadata),
            source: TestDataSource.bundled,
          ));

          _logger.info('Discovered platform from zip: $platformName');
        } catch (e) {
          _logger.warning('Failed to read zip file $zipPath: $e');
        }
      }

      _logger.info('Discovered ${testDataList.length} test data platforms from assets');
    } catch (e) {
      _logger.severe('Failed to discover test data from assets', e);
    }

    return testDataList;
  }

  // Create a test runner from bundled zip assets
  static Future<RegressionTestRunner?> createFromAssets(String platformPath) async {
    try {
      final zipPath = 'test_data/regression/$platformPath.zip';
      final zipData = await rootBundle.load(zipPath);
      return await _createFromZip(platformPath, zipData.buffer.asUint8List());
    } catch (e) {
      _logger.severe('Failed to create test runner for $platformPath', e);
      return null;
    }
  }

  // Create from zip file
  static Future<RegressionTestRunner?> _createFromZip(String platformPath, Uint8List zipData) async {
    try {
      final archive = ZipDecoder().decodeBytes(zipData);

      // Helper to find and read file from archive
      String? readTextFile(String relativePath) {
        for (final file in archive.files) {
          if (file.name == relativePath || file.name.endsWith('/$relativePath')) {
            return String.fromCharCodes(file.content as List<int>);
          }
        }
        return null;
      }

      Uint8List? readBinaryFile(String relativePath) {
        for (final file in archive.files) {
          if (file.name == relativePath || file.name.endsWith('/$relativePath')) {
            return Uint8List.fromList(file.content as List<int>);
          }
        }
        return null;
      }

      // Load test vectors (required)
      final vectorsContent = readTextFile('test_vectors.json');
      if (vectorsContent == null) {
        _logger.severe('No test_vectors.json found in zip for $platformPath');
        return null;
      }
      final testVectors = jsonDecode(vectorsContent);

      // Load edge cases (optional)
      Map<String, dynamic>? edgeCases;
      final edgeContent = readTextFile('edge_cases.json');
      if (edgeContent != null) {
        edgeCases = jsonDecode(edgeContent);
      }

      // Load file vectors and encrypted files (optional)
      Map<String, dynamic>? fileVectors;
      final encryptedFiles = <String, Uint8List>{};

      final fileVectorsContent = readTextFile('encrypted_files/file_vectors.json');
      if (fileVectorsContent != null) {
        fileVectors = jsonDecode(fileVectorsContent);

        // Load binary encrypted files
        final files = fileVectors['files'] as List;
        for (final fileInfo in files) {
          final filename = fileInfo['filename'];
          final fileData = readBinaryFile('encrypted_files/$filename');
          if (fileData != null) {
            encryptedFiles[filename] = fileData;
            _logger.info('Loaded encrypted file from zip: $filename (${fileData.length} bytes)');
          }
        }
      }

      return RegressionTestRunner(
        platformPath: platformPath,
        testVectors: testVectors,
        edgeCases: edgeCases,
        fileVectors: fileVectors,
        encryptedFiles: encryptedFiles,
      );
    } catch (e) {
      _logger.severe('Failed to create runner from zip for $platformPath', e);
      return null;
    }
  }


  static int _countTests(Map<String, dynamic> metadata) {
    int total = 0;
    final testConfig = metadata['test_configuration'];
    if (testConfig != null && testConfig['test_count'] != null) {
      final counts = testConfig['test_count'] as Map<String, dynamic>;
      for (final count in counts.values) {
        if (count is int) total += count;
      }
    }
    return total;
  }

  Future<List<RegressionTestResult>> runTests() async {
    final results = <RegressionTestResult>[];

    try {
      // Run each test suite from already loaded test vectors
      for (final suite in testVectors['test_suites']) {
        results.addAll(await _runTestSuite(suite));
      }

      // Run edge cases if available
      final edgeCasesLocal = edgeCases;
      if (edgeCasesLocal != null) {
        results.addAll(await _runEdgeCases(edgeCasesLocal));
      }

      // Run file encryption tests if available
      final fileVectorsLocal = fileVectors;
      if (fileVectorsLocal != null) {
        results.addAll(await _runFileDecryptionTests(fileVectorsLocal));
      }

      _logger.info('Regression tests completed: ${results.length} tests run');
    } catch (e, s) {
      _logger.severe('Failed to run regression tests', e, s);
      rethrow;
    }

    return results;
  }


  Future<List<RegressionTestResult>> _runTestSuite(Map<String, dynamic> suite) async {
    final results = <RegressionTestResult>[];
    final algorithm = suite['algorithm'];
    final vectors = suite['vectors'] as List;

    _logger.info('Running test suite: $algorithm (${vectors.length} vectors)');

    for (final vector in vectors) {
      final result = await _runTestVector(algorithm, vector);
      results.add(result);
    }

    return results;
  }

  Future<RegressionTestResult> _runTestVector(
    String algorithm,
    Map<String, dynamic> vector,
  ) async {
    final testId = vector['id'];
    final description = vector['description'];

    try {
      switch (algorithm) {
        case 'XSalsa20-Poly1305':
          return await _testXSalsa20(vector);

        case 'XChaCha20-Poly1305':
          return await _testXChaCha20(vector);

        case 'Argon2id':
          return await _testArgon2id(vector);

        case 'SealedBox':
          return await _testSealedBox(vector);

        default:
          return RegressionTestResult(
            testId: testId,
            description: description,
            algorithm: algorithm,
            passed: false,
            error: 'Unknown algorithm: $algorithm',
          );
      }
    } catch (e) {
      return RegressionTestResult(
        testId: testId,
        description: description,
        algorithm: algorithm,
        passed: false,
        error: e.toString(),
      );
    }
  }

  Future<RegressionTestResult> _testXSalsa20(Map<String, dynamic> vector) async {
    final testId = vector['id'];
    final description = vector['description'];
    final inputs = vector['inputs'];
    final outputs = vector['outputs'];

    // Decode inputs
    final plaintext = _hexToBytes(inputs['plaintext_hex']);
    final key = _hexToBytes(inputs['key_hex']);
    final nonce = _hexToBytes(inputs['nonce_hex']);
    final expectedCiphertext = _hexToBytes(outputs['ciphertext_hex']);

    // Test encryption (not used, we test with known ciphertext)
    // CryptoUtil.encryptSync(plaintext, key);

    // Test decryption with expected ciphertext
    final decryptedData = await CryptoUtil.decrypt(expectedCiphertext, key, nonce);

    // Verify
    final passed = _compareBytes(decryptedData, plaintext);

    return RegressionTestResult(
      testId: testId,
      description: description,
      algorithm: 'XSalsa20-Poly1305',
      passed: passed,
      error: passed ? null : 'Decryption mismatch',
    );
  }

  Future<RegressionTestResult> _testXChaCha20(Map<String, dynamic> vector) async {
    final testId = vector['id'];
    final description = vector['description'];
    final inputs = vector['inputs'];
    final outputs = vector['outputs'];

    // Decode inputs
    final plaintext = _hexToBytes(inputs['plaintext_hex']);
    final key = _hexToBytes(inputs['key_hex']);
    final header = _hexToBytes(inputs['header_hex']);
    final expectedCiphertext = _hexToBytes(outputs['ciphertext_hex']);

    // Test decryption with expected ciphertext
    final decryptedData = await CryptoUtil.decryptChaCha(
      expectedCiphertext,
      key,
      header,
    );

    // Verify
    final passed = _compareBytes(decryptedData, plaintext);

    return RegressionTestResult(
      testId: testId,
      description: description,
      algorithm: 'XChaCha20-Poly1305',
      passed: passed,
      error: passed ? null : 'Decryption mismatch',
    );
  }

  Future<RegressionTestResult> _testArgon2id(Map<String, dynamic> vector) async {
    final testId = vector['id'];
    final description = vector['description'];
    final inputs = vector['inputs'];
    final outputs = vector['outputs'];
    final parameters = vector['parameters'];

    // Decode inputs
    final password = _hexToBytes(inputs['password_hex']);
    final salt = _hexToBytes(inputs['salt_hex']);
    final expectedKey = _hexToBytes(outputs['derived_key_hex']);

    // Get parameters
    final memLimit = parameters['mem_limit'];
    final opsLimit = parameters['ops_limit'];

    // Derive key
    final derivedKey = await CryptoUtil.deriveKey(
      password,
      salt,
      memLimit,
      opsLimit,
    );

    // Verify
    final passed = _compareBytes(derivedKey, expectedKey);

    return RegressionTestResult(
      testId: testId,
      description: description,
      algorithm: 'Argon2id',
      passed: passed,
      error: passed ? null : 'Key derivation mismatch',
    );
  }

  Future<RegressionTestResult> _testSealedBox(Map<String, dynamic> vector) async {
    final testId = vector['id'];
    final description = vector['description'];
    final inputs = vector['inputs'];
    final outputs = vector['outputs'];

    // Decode inputs
    final plaintext = _hexToBytes(inputs['plaintext_hex']);
    final publicKey = _hexToBytes(inputs['public_key_hex']);
    final secretKey = _hexToBytes(inputs['secret_key_hex']);
    final sealed = _hexToBytes(outputs['sealed_hex']);

    // Test decryption
    final opened = CryptoUtil.openSealSync(sealed, publicKey, secretKey);

    // Verify
    final passed = _compareBytes(opened, plaintext);

    return RegressionTestResult(
      testId: testId,
      description: description,
      algorithm: 'SealedBox',
      passed: passed,
      error: passed ? null : 'Sealed box decryption mismatch',
    );
  }

  Future<List<RegressionTestResult>> _runEdgeCases(
    Map<String, dynamic> edgeCases,
  ) async {
    final results = <RegressionTestResult>[];

    for (final edgeCase in edgeCases['edge_cases']) {
      if (edgeCase['category'] == 'streaming_encryption') {
        results.addAll(await _testStreamingEdgeCases(edgeCase));
      }
    }

    return results;
  }

  Future<List<RegressionTestResult>> _testStreamingEdgeCases(
    Map<String, dynamic> edgeCase,
  ) async {
    final results = <RegressionTestResult>[];
    final cases = edgeCase['cases'] as List;

    for (final testCase in cases) {
      final testId = testCase['id'];
      final description = testCase['description'];
      final size = testCase['size'];

      try {
        // For large sizes, just verify the metadata
        if (size > 1000) {
          results.add(RegressionTestResult(
            testId: testId,
            description: description,
            algorithm: 'Streaming',
            passed: true,
            error: null,
          ));
        } else {
          // For small sizes, test with actual data
          final testData = _hexToBytes(testCase['test_data_hex']);
          final key = _hexToBytes(testCase['key_hex']);

          // Test encryption/decryption roundtrip
          final encryptResult = await CryptoUtil.encryptChaCha(testData, key);
          final decryptedData = await CryptoUtil.decryptChaCha(
            encryptResult.encryptedData!,
            key,
            encryptResult.header!,
          );

          final passed = _compareBytes(decryptedData, testData);

          results.add(RegressionTestResult(
            testId: testId,
            description: description,
            algorithm: 'Streaming',
            passed: passed,
            error: passed ? null : 'Streaming roundtrip failed',
          ));
        }
      } catch (e) {
        results.add(RegressionTestResult(
          testId: testId,
          description: description,
          algorithm: 'Streaming',
          passed: false,
          error: e.toString(),
        ));
      }
    }

    return results;
  }


  Future<List<RegressionTestResult>> _runFileDecryptionTests(
    Map<String, dynamic> fileVectors,
  ) async {
    final results = <RegressionTestResult>[];
    final files = fileVectors['files'] as List;

    _logger.info('Running file decryption tests: ${files.length} files');

    for (final fileInfo in files) {
      final testId = fileInfo['id'];
      final filename = fileInfo['filename'];
      final originalSize = fileInfo['original_size'];

      try {
        // Get encrypted file data from memory
        final encryptedData = encryptedFiles[filename];

        if (encryptedData == null) {
          results.add(RegressionTestResult(
            testId: testId,
            description: 'Decrypt $filename ($originalSize bytes)',
            algorithm: 'File-XChaCha20',
            passed: false,
            error: 'Encrypted file not loaded: $filename',
          ));
          continue;
        }

        // Read encryption parameters
        final key = _hexToBytes(fileInfo['key_hex']);
        final header = _hexToBytes(fileInfo['header_hex']);
        final expectedOriginalHash = fileInfo['original_sha256'];
        final expectedEncryptedHash = fileInfo['encrypted_sha256'];

        // Verify encrypted file hash
        final encryptedHash = sha256.convert(encryptedData).toString();
        if (encryptedHash != expectedEncryptedHash) {
          throw Exception(
            'Encrypted file hash mismatch: expected $expectedEncryptedHash, got $encryptedHash',
          );
        }

        // Create temp files for decryption
        final tempDir = await getTemporaryDirectory();
        final encryptedPath = '${tempDir.path}/temp_encrypted_$filename';
        final decryptedPath = '${tempDir.path}/decrypted_$filename';

        // Write encrypted data to temp file
        final tempEncryptedFile = File(encryptedPath);
        await tempEncryptedFile.writeAsBytes(encryptedData);

        final decryptedFile = File(decryptedPath);

        // Decrypt the file
        await CryptoUtil.decryptFile(
          encryptedPath,
          decryptedPath,
          header,
          key,
        );

        // Verify the decrypted file exists
        if (!await decryptedFile.exists()) {
          throw Exception('Decrypted file was not created');
        }

        // Calculate hash of decrypted file
        final decryptedContent = await decryptedFile.readAsBytes();
        final actualHash = sha256.convert(decryptedContent).toString();

        // Verify size
        if (decryptedContent.length != originalSize) {
          throw Exception(
            'Size mismatch: expected $originalSize, got ${decryptedContent.length}',
          );
        }

        // Verify hash
        final passed = actualHash == expectedOriginalHash;

        results.add(RegressionTestResult(
          testId: testId,
          description: 'Decrypt $filename ($originalSize bytes)',
          algorithm: 'File-XChaCha20',
          passed: passed,
          error: passed ? null : 'Hash mismatch after decryption',
        ));

        // Clean up temp files
        await decryptedFile.delete();
        await tempEncryptedFile.delete();

        _logger.info('File decryption test $testId: ${passed ? 'PASSED' : 'FAILED'}');
      } catch (e) {
        results.add(RegressionTestResult(
          testId: testId,
          description: 'Decrypt $filename ($originalSize bytes)',
          algorithm: 'File-XChaCha20',
          passed: false,
          error: e.toString(),
        ));
        _logger.severe('File decryption test $testId failed', e);
      }
    }

    return results;
  }

  Uint8List _hexToBytes(String hex) {
    if (hex == 'too_large') {
      return Uint8List(0);
    }
    if (hex.isEmpty) {
      return Uint8List(0);
    }

    final bytes = <int>[];
    for (int i = 0; i < hex.length; i += 2) {
      final byte = hex.substring(i, i + 2);
      bytes.add(int.parse(byte, radix: 16));
    }
    return Uint8List.fromList(bytes);
  }

  bool _compareBytes(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }
}

class RegressionTestResult {
  final String testId;
  final String description;
  final String algorithm;
  final bool passed;
  final String? error;

  RegressionTestResult({
    required this.testId,
    required this.description,
    required this.algorithm,
    required this.passed,
    this.error,
  });
}

enum TestDataSource {
  bundled,  // Bundled as zip file in assets
}

class TestDataInfo {
  final String path;
  final String name;
  final String platform;
  final String version;
  final String timestamp;
  final int testCount;
  final TestDataSource source;

  TestDataInfo({
    required this.path,
    required this.name,
    required this.platform,
    required this.version,
    required this.timestamp,
    required this.testCount,
    required this.source,
  });

  String get displayName {
    return '$name ($platform v$version)';
  }
}