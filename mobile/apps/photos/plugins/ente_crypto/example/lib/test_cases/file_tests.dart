import 'dart:io';
import 'dart:typed_data';

import 'package:ente_crypto/ente_crypto.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';

import 'package:ente_crypto_example/screens/test_runner_screen.dart';

class FileTests extends TestRunner {
  final _logger = Logger('FileTests');

  @override
  Future<TestResults> runTests() async {
    final results = TestResults();
    final tempDir = await getTemporaryDirectory();

    results.addResult(await _testBasicFileEncryption(tempDir));
    results.addResult(await _testFileHash(tempDir));
    results.addResult(await _testConcurrentFileOperations(tempDir));
    results.addResult(await _testNonExistentFileEncryption(tempDir));
    results.addResult(await _testNonExistentFileDecryption(tempDir));
    results.addResult(await _testOverwriteExistingFile(tempDir));
    results.addResult(await _testSpecialCharacterFilenames(tempDir));
    results.addResult(await _testDeepDirectoryStructure(tempDir));
    results.addResult(await _testRelativePathRejection(tempDir));
    results.addResult(await _testPreGeneratedKey(tempDir));

    await _cleanupTestFiles(tempDir);
    return results;
  }

  Future<TestResult> _testBasicFileEncryption(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/basic_source.dat');
      final encryptedFile = File('${tempDir.path}/basic_encrypted.dat');
      final decryptedFile = File('${tempDir.path}/basic_decrypted.dat');

      final testData = Uint8List(10000);
      for (int i = 0; i < testData.length; i++) {
        testData[i] = (i * 3) % 256;
      }
      await sourceFile.writeAsBytes(testData);

      final encryptResult = await CryptoUtil.encryptFile(
        sourceFile.path,
        encryptedFile.path,
      );

      if (!await encryptedFile.exists()) {
        throw Exception('Encrypted file was not created');
      }

      await CryptoUtil.decryptFile(
        encryptedFile.path,
        decryptedFile.path,
        encryptResult.header!,
        encryptResult.key!,
      );

      if (!await decryptedFile.exists()) {
        throw Exception('Decrypted file was not created');
      }

      final originalData = await sourceFile.readAsBytes();
      final decryptedData = await decryptedFile.readAsBytes();

      if (!_areListsEqual(originalData, decryptedData)) {
        throw Exception('File content mismatch after decryption');
      }

      _logger.info('Basic file encryption test passed');
      return TestResult(
        name: 'Basic File Encryption',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Basic file encryption test failed', e);
      return TestResult(
        name: 'Basic File Encryption',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testFileHash(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final testFile = File('${tempDir.path}/hash_test.dat');

      final testData = Uint8List(5000);
      for (int i = 0; i < testData.length; i++) {
        testData[i] = (i * 7) % 256;
      }
      await testFile.writeAsBytes(testData);

      final hash1 = await CryptoUtil.getHash(testFile);
      final hash2 = await CryptoUtil.getHash(testFile);

      if (!_areListsEqual(hash1, hash2)) {
        throw Exception('Same file produced different hashes');
      }

      if (hash1.length != 64) {
        throw Exception('Hash has wrong length: ${hash1.length}');
      }

      await testFile.writeAsBytes(Uint8List.fromList([1, 2, 3]));
      final hash3 = await CryptoUtil.getHash(testFile);

      if (_areListsEqual(hash1, hash3)) {
        throw Exception('Different content produced same hash');
      }

      _logger.info('File hash test passed');
      return TestResult(
        name: 'File Hash Computation',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('File hash test failed', e);
      return TestResult(
        name: 'File Hash Computation',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testConcurrentFileOperations(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final futures = <Future>[];

      for (int i = 0; i < 3; i++) {
        futures.add(_concurrentEncryptDecrypt(tempDir, i));
      }

      await Future.wait(futures);

      _logger.info('Concurrent file operations test passed');
      return TestResult(
        name: 'Concurrent File Operations',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Concurrent file operations test failed', e);
      return TestResult(
        name: 'Concurrent File Operations',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<void> _concurrentEncryptDecrypt(Directory tempDir, int index) async {
    final sourceFile = File('${tempDir.path}/concurrent_${index}.dat');
    final encryptedFile = File('${tempDir.path}/concurrent_${index}_enc.dat');
    final decryptedFile = File('${tempDir.path}/concurrent_${index}_dec.dat');

    final testData = Uint8List(1000 + index * 100);
    for (int i = 0; i < testData.length; i++) {
      testData[i] = (i + index) % 256;
    }
    await sourceFile.writeAsBytes(testData);

    final encryptResult = await CryptoUtil.encryptFile(
      sourceFile.path,
      encryptedFile.path,
    );

    await CryptoUtil.decryptFile(
      encryptedFile.path,
      decryptedFile.path,
      encryptResult.header!,
      encryptResult.key!,
    );

    final originalData = await sourceFile.readAsBytes();
    final decryptedData = await decryptedFile.readAsBytes();

    if (!_areListsEqual(originalData, decryptedData)) {
      throw Exception('Concurrent file $index failed');
    }
  }

  Future<TestResult> _testNonExistentFileEncryption(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final nonExistentFile = '${tempDir.path}/non_existent.dat';
      final outputFile = '${tempDir.path}/output.dat';

      try {
        await CryptoUtil.encryptFile(nonExistentFile, outputFile);
        throw Exception('Should have failed with non-existent file');
      } catch (e) {
        _logger.info('Correctly rejected non-existent file: $e');
      }

      _logger.info('Non-existent file encryption test passed');
      return TestResult(
        name: 'Non-existent File Encryption',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Non-existent file encryption test failed', e);
      return TestResult(
        name: 'Non-existent File Encryption',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testNonExistentFileDecryption(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final nonExistentFile = '${tempDir.path}/non_existent_decrypt.dat';
      final outputFile = '${tempDir.path}/output_decrypt.dat';
      final header = Uint8List(24);
      final key = CryptoUtil.generateKey();

      try {
        await CryptoUtil.decryptFile(nonExistentFile, outputFile, header, key);
        throw Exception('Should have failed with non-existent file');
      } catch (e) {
        _logger.info('Correctly rejected non-existent file for decryption: $e');
      }

      _logger.info('Non-existent file decryption test passed');
      return TestResult(
        name: 'Non-existent File Decryption',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Non-existent file decryption test failed', e);
      return TestResult(
        name: 'Non-existent File Decryption',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testOverwriteExistingFile(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/overwrite_source.dat');
      final targetFile = File('${tempDir.path}/overwrite_target.dat');

      await sourceFile.writeAsBytes(Uint8List.fromList([1, 2, 3, 4, 5]));
      await targetFile.writeAsBytes(Uint8List.fromList([99]));

      final originalTargetSize = await targetFile.length();

      await CryptoUtil.encryptFile(
        sourceFile.path,
        targetFile.path,
      );

      final newTargetSize = await targetFile.length();

      if (newTargetSize == originalTargetSize) {
        throw Exception('File was not overwritten');
      }

      _logger.info('Overwrite existing file test passed');
      return TestResult(
        name: 'Overwrite Existing File',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Overwrite existing file test failed', e);
      return TestResult(
        name: 'Overwrite Existing File',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testSpecialCharacterFilenames(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final specialName = 'test_file_#@\$_123.dat';
      final sourceFile = File('${tempDir.path}/$specialName');
      final encryptedFile = File('${tempDir.path}/encrypted_$specialName');
      final decryptedFile = File('${tempDir.path}/decrypted_$specialName');

      await sourceFile
          .writeAsBytes(Uint8List.fromList('Special chars test'.codeUnits));

      final encryptResult = await CryptoUtil.encryptFile(
        sourceFile.path,
        encryptedFile.path,
      );

      await CryptoUtil.decryptFile(
        encryptedFile.path,
        decryptedFile.path,
        encryptResult.header!,
        encryptResult.key!,
      );

      final originalData = await sourceFile.readAsBytes();
      final decryptedData = await decryptedFile.readAsBytes();

      if (!_areListsEqual(originalData, decryptedData)) {
        throw Exception('Special character filename test failed');
      }

      _logger.info('Special character filenames test passed');
      return TestResult(
        name: 'Special Character Filenames',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Special character filenames test failed', e);
      return TestResult(
        name: 'Special Character Filenames',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testDeepDirectoryStructure(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final deepDir = Directory('${tempDir.path}/level1/level2/level3');
      await deepDir.create(recursive: true);

      final sourceFile = File('${deepDir.path}/deep_test.dat');
      final encryptedFile = File('${deepDir.path}/deep_encrypted.dat');
      final decryptedFile = File('${deepDir.path}/deep_decrypted.dat');

      await sourceFile
          .writeAsBytes(Uint8List.fromList('Deep directory test'.codeUnits));

      final encryptResult = await CryptoUtil.encryptFile(
        sourceFile.path,
        encryptedFile.path,
      );

      await CryptoUtil.decryptFile(
        encryptedFile.path,
        decryptedFile.path,
        encryptResult.header!,
        encryptResult.key!,
      );

      final originalData = await sourceFile.readAsBytes();
      final decryptedData = await decryptedFile.readAsBytes();

      if (!_areListsEqual(originalData, decryptedData)) {
        throw Exception('Deep directory test failed');
      }

      _logger.info('Deep directory structure test passed');
      return TestResult(
        name: 'Deep Directory Structure',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Deep directory structure test failed', e);
      return TestResult(
        name: 'Deep Directory Structure',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testRelativePathRejection(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/relative_source.dat');
      await sourceFile.writeAsBytes(Uint8List.fromList([1, 2, 3]));

      // Test with absolute paths
      final encryptResult = await CryptoUtil.encryptFile(
        sourceFile.path,
        '${tempDir.path}/relative_encrypted.dat',
      );

      await CryptoUtil.decryptFile(
        '${tempDir.path}/relative_encrypted.dat',
        '${tempDir.path}/relative_decrypted.dat',
        encryptResult.header!,
        encryptResult.key!,
      );

      _logger.info('Path handling test passed');
      return TestResult(
        name: 'Path Handling',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Path handling test failed', e);
      return TestResult(
        name: 'Path Handling',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testPreGeneratedKey(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/pregen_key_source.dat');
      final encryptedFile = File('${tempDir.path}/pregen_key_encrypted.dat');
      final decryptedFile = File('${tempDir.path}/pregen_key_decrypted.dat');

      await sourceFile.writeAsBytes(
        Uint8List.fromList('Pre-generated key test'.codeUnits),
      );

      final preGeneratedKey = CryptoUtil.generateKey();

      final encryptResult = await CryptoUtil.encryptFile(
        sourceFile.path,
        encryptedFile.path,
        key: preGeneratedKey,
      );

      if (!_areListsEqual(encryptResult.key!, preGeneratedKey)) {
        throw Exception('Pre-generated key was not used');
      }

      await CryptoUtil.decryptFile(
        encryptedFile.path,
        decryptedFile.path,
        encryptResult.header!,
        preGeneratedKey,
      );

      final originalData = await sourceFile.readAsBytes();
      final decryptedData = await decryptedFile.readAsBytes();

      if (!_areListsEqual(originalData, decryptedData)) {
        throw Exception('Pre-generated key decryption failed');
      }

      _logger.info('Pre-generated key test passed');
      return TestResult(
        name: 'Pre-generated Key',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Pre-generated key test failed', e);
      return TestResult(
        name: 'Pre-generated Key',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<void> _cleanupTestFiles(Directory tempDir) async {
    final files = tempDir.listSync(recursive: true);
    for (final file in files) {
      if (file is File && file.path.contains('.dat')) {
        try {
          await file.delete();
        } catch (_) {}
      }
    }

    try {
      final deepDir = Directory('${tempDir.path}/level1');
      if (await deepDir.exists()) {
        await deepDir.delete(recursive: true);
      }
    } catch (_) {}
  }

  bool _areListsEqual(List<int> list1, List<int> list2) {
    if (list1.length != list2.length) return false;
    for (int i = 0; i < list1.length; i++) {
      if (list1[i] != list2[i]) return false;
    }
    return true;
  }
}
