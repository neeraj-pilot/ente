import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:ente_crypto/ente_crypto.dart';
import 'package:flutter/services.dart';
import 'package:logging/logging.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';

class TestDataExporter {
  static final _logger = Logger('TestDataExporter');
  static const String _testDataPath = '/Users/duckydev/test-data/crypto';
  static const String _platformPrefix = 'droid';
  static const String _version = 'v1.0';

  // Use deterministic random for reproducibility
  static final _random = Random(42);

  static Future<void> exportTestData() async {
    try {
      _logger.info('Starting test data export to $_testDataPath');

      // Create directory structure
      final testDir = Directory('$_testDataPath/$_platformPrefix-$_version');
      if (!await testDir.exists()) {
        await testDir.create(recursive: true);
      }

      // Generate metadata
      final metadata = await _generateMetadata();

      // Generate test vectors
      final testVectors = await _generateTestVectors();

      // Generate edge cases
      final edgeCases = await _generateEdgeCases();

      // Generate file encryption test vectors
      await _generateFileEncryptionVectors(testDir);

      // Write files
      await _writeJsonFile('$testDir/metadata.json', metadata);
      await _writeJsonFile('$testDir/test_vectors.json', testVectors);
      await _writeJsonFile('$testDir/edge_cases.json', edgeCases);

      _logger.info('Test data exported successfully to ${testDir.path}');

      // Return the path for UI feedback
      return;
    } catch (e, s) {
      _logger.severe('Failed to export test data', e, s);
      rethrow;
    }
  }

  static Future<Map<String, dynamic>> _generateMetadata() async {
    final packageInfo = await PackageInfo.fromPlatform();

    return {
      'version': '1.0.0',
      'platform': _platformPrefix,
      'timestamp': DateTime.now().toUtc().toIso8601String(),
      'generator': {
        'library': 'ente_crypto',
        'library_version': '0.0.1',
        'language': 'dart',
        'dart_version': Platform.version.split(' ')[0],
        'flutter_version': '3.32.8',
        'app_name': packageInfo.appName,
        'app_version': packageInfo.version,
        'app_build': packageInfo.buildNumber,
        'sodium_library': 'flutter_sodium',
        'sodium_version': '0.2.0',
      },
      'device': {
        'platform': Platform.operatingSystem,
        'platform_version': Platform.operatingSystemVersion,
        'locale': Platform.localeName,
        'numberOfProcessors': Platform.numberOfProcessors,
      },
      'test_configuration': {
        'random_seed': 42,
        'deterministic': true,
        'test_count': {
          'xsalsa20': 6,
          'xchacha20': 5,
          'xchacha20_stream': 6,
          'argon2id': 12,
          'sealed_box': 5,
          'file_encryption': 5,
        },
      },
    };
  }

  static Future<Map<String, dynamic>> _generateTestVectors() async {
    return {
      'version': '1.0.0',
      'platform': _platformPrefix,
      'timestamp': DateTime.now().toUtc().toIso8601String(),
      'test_suites': [
        await _generateXSalsa20Suite(),
        await _generateXChaCha20Suite(),
        await _generateArgon2idSuite(),
        await _generateSealedBoxSuite(),
      ],
    };
  }

  static Future<Map<String, dynamic>> _generateXSalsa20Suite() async {
    final vectors = <Map<String, dynamic>>[];

    for (final size in [0, 1, 16, 256, 1024, 4096]) {
      final plaintext = _generateDeterministicBytes(size);
      final key = _generateDeterministicKey();

      final encryptedData = CryptoUtil.encryptSync(plaintext, key);

      // Extract the nonce from the encrypted data (first 24 bytes for XSalsa20)
      final actualNonce = encryptedData.nonce ??
          (encryptedData.encryptedData!.length >= 24
            ? encryptedData.encryptedData!.sublist(0, 24)
            : Uint8List(24));

      vectors.add({
        'id': 'xsalsa20_${_platformPrefix}_${size.toString().padLeft(5, '0')}',
        'description': 'XSalsa20-Poly1305 with $size byte plaintext',
        'inputs': {
          'plaintext_hex': _bytesToHex(plaintext),
          'plaintext_base64': base64Encode(plaintext),
          'key_hex': _bytesToHex(key),
          'key_base64': base64Encode(key),
          'nonce_hex': _bytesToHex(actualNonce),
          'nonce_base64': base64Encode(actualNonce),
        },
        'outputs': {
          'ciphertext_hex': _bytesToHex(encryptedData.encryptedData!),
          'ciphertext_base64': base64Encode(encryptedData.encryptedData!),
          'mac_included': true,
        },
        'metadata': {
          'plaintext_size': size,
          'ciphertext_size': encryptedData.encryptedData!.length,
          'key_size': 32,
          'nonce_size': 24,
          'mac_size': 16,
        },
      });
    }

    return {
      'algorithm': 'XSalsa20-Poly1305',
      'description': 'Authenticated encryption using XSalsa20 stream cipher with Poly1305 MAC',
      'vectors': vectors,
    };
  }

  static Future<Map<String, dynamic>> _generateXChaCha20Suite() async {
    final vectors = <Map<String, dynamic>>[];

    for (final size in [0, 1, 100, 1000, 10000]) {
      final plaintext = _generateDeterministicBytes(size);
      final key = _generateDeterministicKey();

      final encryptionResult = await CryptoUtil.encryptChaCha(plaintext, key);

      vectors.add({
        'id': 'xchacha20_${_platformPrefix}_${size.toString().padLeft(5, '0')}',
        'description': 'XChaCha20-Poly1305 with $size byte plaintext',
        'inputs': {
          'plaintext_hex': _bytesToHex(plaintext),
          'plaintext_base64': base64Encode(plaintext),
          'key_hex': _bytesToHex(key),
          'key_base64': base64Encode(key),
          'header_hex': _bytesToHex(encryptionResult.header!),
          'header_base64': base64Encode(encryptionResult.header!),
        },
        'outputs': {
          'ciphertext_hex': _bytesToHex(encryptionResult.encryptedData!),
          'ciphertext_base64': base64Encode(encryptionResult.encryptedData!),
          'mac_included': true,
        },
        'metadata': {
          'plaintext_size': size,
          'ciphertext_size': encryptionResult.encryptedData!.length,
          'key_size': 32,
          'header_size': 24,
        },
      });
    }

    return {
      'algorithm': 'XChaCha20-Poly1305',
      'description': 'Authenticated encryption using XChaCha20 stream cipher with Poly1305 MAC',
      'vectors': vectors,
    };
  }

  static Future<Map<String, dynamic>> _generateArgon2idSuite() async {
    final vectors = <Map<String, dynamic>>[];

    final testPasswords = [
      '',
      'a',
      'password',
      'MySecurePassword123!',
      'test' * 25,
      r'Special!@#$%^&*()_+{}|:"<>?[]\;,./',
    ];

    for (int i = 0; i < testPasswords.length; i++) {
      final password = Uint8List.fromList(testPasswords[i].codeUnits);
      final salt = _generateDeterministicSalt();

      // Interactive variant
      final interactiveResult = await CryptoUtil.deriveInteractiveKey(password, salt);

      vectors.add({
        'id': 'argon2id_interactive_${_platformPrefix}_${i.toString().padLeft(3, '0')}',
        'description': 'Argon2id interactive with password length ${password.length}',
        'variant': 'interactive',
        'inputs': {
          'password_hex': _bytesToHex(password),
          'password_base64': base64Encode(password),
          'salt_hex': _bytesToHex(salt),
          'salt_base64': base64Encode(salt),
        },
        'outputs': {
          'derived_key_hex': _bytesToHex(interactiveResult.key),
          'derived_key_base64': base64Encode(interactiveResult.key),
        },
        'parameters': {
          'mem_limit': interactiveResult.memLimit,
          'ops_limit': interactiveResult.opsLimit,
          'algorithm': 'argon2id13',
        },
        'metadata': {
          'password_length': password.length,
          'salt_size': 16,
          'key_size': 32,
        },
      });
    }

    return {
      'algorithm': 'Argon2id',
      'description': 'Password-based key derivation using Argon2id v1.3',
      'vectors': vectors,
    };
  }

  static Future<Map<String, dynamic>> _generateSealedBoxSuite() async {
    final vectors = <Map<String, dynamic>>[];

    for (final size in [0, 1, 32, 128, 512]) {
      final plaintext = _generateDeterministicBytes(size);
      final keyPair = await CryptoUtil.generateKeyPair();

      final sealed = CryptoUtil.sealSync(plaintext, keyPair.pk);

      vectors.add({
        'id': 'sealedbox_${_platformPrefix}_${size.toString().padLeft(5, '0')}',
        'description': 'Sealed box with $size byte plaintext',
        'inputs': {
          'plaintext_hex': _bytesToHex(plaintext),
          'plaintext_base64': base64Encode(plaintext),
          'public_key_hex': _bytesToHex(keyPair.pk),
          'public_key_base64': base64Encode(keyPair.pk),
          'secret_key_hex': _bytesToHex(keyPair.sk),
          'secret_key_base64': base64Encode(keyPair.sk),
        },
        'outputs': {
          'sealed_hex': _bytesToHex(sealed),
          'sealed_base64': base64Encode(sealed),
        },
        'metadata': {
          'plaintext_size': size,
          'sealed_size': sealed.length,
          'public_key_size': 32,
          'secret_key_size': 32,
        },
      });
    }

    return {
      'algorithm': 'SealedBox',
      'description': 'Anonymous encryption using sealed box (X25519 + XSalsa20-Poly1305)',
      'vectors': vectors,
    };
  }

  static Future<Map<String, dynamic>> _generateEdgeCases() async {
    return {
      'version': '1.0.0',
      'platform': _platformPrefix,
      'timestamp': DateTime.now().toUtc().toIso8601String(),
      'edge_cases': [
        await _generateStreamingEdgeCases(),
      ],
    };
  }

  static Future<Map<String, dynamic>> _generateStreamingEdgeCases() async {
    final cases = <Map<String, dynamic>>[];
    const chunkSize = 4 * 1024 * 1024;

    // Edge case sizes
    final testSizes = [
      0,                    // Empty
      1,                    // Single byte
      chunkSize - 1,        // Just under chunk boundary
      chunkSize,            // Exact chunk boundary
      chunkSize + 1,        // Just over chunk boundary
      chunkSize * 3 + 100,  // Multiple chunks with remainder
    ];

    for (final size in testSizes) {
      final testData = _generateDeterministicBytes(size);
      final key = _generateDeterministicKey();

      cases.add({
        'id': 'streaming_edge_${_platformPrefix}_$size',
        'description': 'Streaming with size $size (chunk size: $chunkSize)',
        'size': size,
        'chunk_size': chunkSize,
        'test_data_hex': size <= 1000 ? _bytesToHex(testData) : 'too_large',
        'test_data_base64': size <= 1000 ? base64Encode(testData) : 'too_large',
        'key_hex': _bytesToHex(key),
        'key_base64': base64Encode(key),
        'expected_chunks': (size / chunkSize).ceil(),
      });
    }

    return {
      'category': 'streaming_encryption',
      'description': 'Edge cases for chunked streaming encryption',
      'chunk_size': chunkSize,
      'cases': cases,
    };
  }

  static Uint8List _generateDeterministicBytes(int size) {
    final bytes = Uint8List(size);
    for (int i = 0; i < size; i++) {
      bytes[i] = (_random.nextInt(256) + i) % 256;
    }
    return bytes;
  }

  static Uint8List _generateDeterministicKey() {
    return _generateDeterministicBytes(32);
  }

  static Uint8List _generateDeterministicSalt() {
    return _generateDeterministicBytes(16);
  }

  static String _bytesToHex(Uint8List bytes) {
    return bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }

  static Future<void> _generateFileEncryptionVectors(Directory testDir) async {
    _logger.info('Generating file encryption test vectors');

    // Create encrypted_files directory
    final encryptedFilesDir = Directory('${testDir.path}/encrypted_files');
    if (!await encryptedFilesDir.exists()) {
      await encryptedFilesDir.create(recursive: true);
    }

    final fileVectors = <Map<String, dynamic>>[];

    // Test file sizes - keeping them small for efficiency
    final testFiles = [
      {'name': 'empty', 'size': 0},
      {'name': 'small_100b', 'size': 100},
      {'name': 'medium_4kb', 'size': 4 * 1024},
      {'name': 'large_1mb', 'size': 1024 * 1024},
      {'name': 'chunked_4mb', 'size': 4 * 1024 * 1024 + 100}, // Just over 4MB chunk boundary
    ];

    // Use a deterministic key for all files
    final key = _generateDeterministicKey();

    for (final fileInfo in testFiles) {
      final name = fileInfo['name'] as String;
      final size = fileInfo['size'] as int;

      _logger.info('Generating encrypted file: $name (size: $size bytes)');

      // Create source file with deterministic content
      final sourceFile = File('${encryptedFilesDir.path}/${name}_plain.tmp');
      final encryptedFile = File('${encryptedFilesDir.path}/$name.enc');

      // Generate deterministic content
      final content = _generateDeterministicBytes(size);
      await sourceFile.writeAsBytes(content);

      // Calculate SHA256 of original content
      final originalHash = sha256.convert(content).toString();

      // Encrypt the file
      final encryptResult = await CryptoUtil.encryptFile(
        sourceFile.path,
        encryptedFile.path,
        key: key,
      );

      // Read encrypted file for hash
      final encryptedContent = await encryptedFile.readAsBytes();
      final encryptedHash = sha256.convert(encryptedContent).toString();

      // Store metadata
      fileVectors.add({
        'id': 'file_${name}_$_platformPrefix',
        'filename': '$name.enc',
        'original_size': size,
        'encrypted_size': encryptedContent.length,
        'key_hex': _bytesToHex(key),
        'key_base64': base64Encode(key),
        'header_hex': _bytesToHex(encryptResult.header!),
        'header_base64': base64Encode(encryptResult.header!),
        'original_sha256': originalHash,
        'encrypted_sha256': encryptedHash,
        'chunk_size': 4 * 1024 * 1024, // 4MB chunks
        'expected_chunks': (size / (4 * 1024 * 1024)).ceil(),
      });

      // Clean up temporary plain file
      await sourceFile.delete();

      _logger.info('Generated encrypted file: ${encryptedFile.path}');
    }

    // Write file vectors metadata
    await _writeJsonFile(
      '${encryptedFilesDir.path}/file_vectors.json',
      {
        'version': '1.0.0',
        'platform': _platformPrefix,
        'timestamp': DateTime.now().toUtc().toIso8601String(),
        'algorithm': 'XChaCha20-Poly1305-Stream',
        'files': fileVectors,
      },
    );

    _logger.info('File encryption test vectors generated successfully');
  }

  static Future<void> _writeJsonFile(String path, Map<String, dynamic> data) async {
    final file = File(path);
    final encoder = const JsonEncoder.withIndent('  ');
    await file.writeAsString(encoder.convert(data));
    _logger.info('Wrote ${file.path}');
  }

  static Future<bool> testDataExists() async {
    // Check in app cache directory
    final cacheDir = await getTemporaryDirectory();
    final dir = Directory('${cacheDir.path}/$_platformPrefix-$_version');

    if (!await dir.exists()) {
      return false;
    }

    final metadataFile = File('${dir.path}/metadata.json');
    final vectorsFile = File('${dir.path}/test_vectors.json');
    final edgeCasesFile = File('${dir.path}/edge_cases.json');

    return await metadataFile.exists() &&
           await vectorsFile.exists() &&
           await edgeCasesFile.exists();
  }

  static Future<String> getTestDataPath() async {
    final cacheDir = await getTemporaryDirectory();
    return '${cacheDir.path}/$_platformPrefix-$_version';
  }

  // Export test data to app cache directory (accessible via ADB)
  static Future<void> exportTestDataToAppCache({String? folderName}) async {
    try {
      // Get app cache directory
      final cacheDir = await getTemporaryDirectory();
      // Use provided folder name or default
      final actualFolderName = folderName ?? '$_platformPrefix-$_version';
      // Store in current/ subdirectory for locally generated data
      final testDir = Directory('${cacheDir.path}/current/$actualFolderName');

      _logger.info('Starting test data export to app cache: ${testDir.path}');

      // Create directory structure
      if (!await testDir.exists()) {
        await testDir.create(recursive: true);
      }

      // Generate metadata
      final metadata = await _generateMetadata();

      // Generate test vectors
      final testVectors = await _generateTestVectors();

      // Generate edge cases
      final edgeCases = await _generateEdgeCases();

      // Generate file encryption test vectors
      await _generateFileEncryptionVectors(testDir);

      // Write files
      await _writeJsonFile('${testDir.path}/metadata.json', metadata);
      await _writeJsonFile('${testDir.path}/test_vectors.json', testVectors);
      await _writeJsonFile('${testDir.path}/edge_cases.json', edgeCases);

      _logger.info('Test data exported successfully to ${testDir.path}');
    } catch (e, s) {
      _logger.severe('Failed to export test data to app cache', e, s);
      rethrow;
    }
  }

  static Future<String> getAppCachePath() async {
    final cacheDir = await getTemporaryDirectory();
    return cacheDir.path;
  }
}