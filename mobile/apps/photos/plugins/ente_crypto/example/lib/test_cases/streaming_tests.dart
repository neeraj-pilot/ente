import 'dart:io';
import 'dart:typed_data';

import 'package:ente_crypto/ente_crypto.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';

import 'package:ente_crypto_example/screens/test_runner_screen.dart';

class StreamingTests extends TestRunner {
  final _logger = Logger('StreamingTests');
  static const int _chunkSize = 4 * 1024 * 1024;

  @override
  Future<TestResults> runTests() async {
    final results = TestResults();
    final tempDir = await getTemporaryDirectory();

    results.addResult(await _testEmptyFile(tempDir));
    results.addResult(await _testSingleByteFile(tempDir));
    results.addResult(await _testExactChunkSize(tempDir));
    results.addResult(await _testChunkSizeMinusOne(tempDir));
    results.addResult(await _testChunkSizePlusOne(tempDir));
    results.addResult(await _testMultipleChunks(tempDir));
    results.addResult(await _testLargeFile(tempDir));
    results.addResult(await _testStreamingWithVariousDataPatterns(tempDir));
    results.addResult(await _testConcurrentStreaming(tempDir));
    results.addResult(await _testStreamingWithInvalidHeader(tempDir));

    await _cleanupTestFiles(tempDir);
    return results;
  }

  Future<TestResult> _testEmptyFile(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/empty_source.dat');
      final encryptedFile = File('${tempDir.path}/empty_encrypted.dat');
      final decryptedFile = File('${tempDir.path}/empty_decrypted.dat');

      await sourceFile.writeAsBytes(Uint8List(0));

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

      final decryptedData = await decryptedFile.readAsBytes();
      if (decryptedData.isNotEmpty) {
        throw Exception(
            'Empty file decryption failed: got ${decryptedData.length} bytes');
      }

      _logger.info('Empty file streaming test passed');
      return TestResult(
        name: 'Empty File Streaming',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Empty file streaming test failed', e);
      return TestResult(
        name: 'Empty File Streaming',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testSingleByteFile(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/single_byte_source.dat');
      final encryptedFile = File('${tempDir.path}/single_byte_encrypted.dat');
      final decryptedFile = File('${tempDir.path}/single_byte_decrypted.dat');

      await sourceFile.writeAsBytes(Uint8List.fromList([42]));

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
        throw Exception('Single byte file decryption failed');
      }

      _logger.info('Single byte file streaming test passed');
      return TestResult(
        name: 'Single Byte File',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Single byte file streaming test failed', e);
      return TestResult(
        name: 'Single Byte File',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testExactChunkSize(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/exact_chunk_source.dat');
      final encryptedFile = File('${tempDir.path}/exact_chunk_encrypted.dat');
      final decryptedFile = File('${tempDir.path}/exact_chunk_decrypted.dat');

      final testData = Uint8List(_chunkSize);
      for (int i = 0; i < testData.length; i++) {
        testData[i] = i % 256;
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
        throw Exception('Exact chunk size file decryption failed');
      }

      _logger.info('Exact chunk size (${_chunkSize}) test passed');
      return TestResult(
        name: 'Exact Chunk Size (${_chunkSize ~/ 1024 ~/ 1024}MB)',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Exact chunk size test failed', e);
      return TestResult(
        name: 'Exact Chunk Size (${_chunkSize ~/ 1024 ~/ 1024}MB)',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testChunkSizeMinusOne(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/chunk_minus_one_source.dat');
      final encryptedFile =
          File('${tempDir.path}/chunk_minus_one_encrypted.dat');
      final decryptedFile =
          File('${tempDir.path}/chunk_minus_one_decrypted.dat');

      final testData = Uint8List(_chunkSize - 1);
      for (int i = 0; i < testData.length; i++) {
        testData[i] = (i * 7) % 256;
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
        throw Exception('Chunk size - 1 file decryption failed');
      }

      _logger.info('Chunk size - 1 test passed');
      return TestResult(
        name: 'Chunk Size - 1 Byte',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Chunk size - 1 test failed', e);
      return TestResult(
        name: 'Chunk Size - 1 Byte',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testChunkSizePlusOne(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/chunk_plus_one_source.dat');
      final encryptedFile =
          File('${tempDir.path}/chunk_plus_one_encrypted.dat');
      final decryptedFile =
          File('${tempDir.path}/chunk_plus_one_decrypted.dat');

      final testData = Uint8List(_chunkSize + 1);
      for (int i = 0; i < testData.length; i++) {
        testData[i] = (i * 11) % 256;
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
        throw Exception('Chunk size + 1 file decryption failed');
      }

      _logger.info('Chunk size + 1 test passed');
      return TestResult(
        name: 'Chunk Size + 1 Byte',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Chunk size + 1 test failed', e);
      return TestResult(
        name: 'Chunk Size + 1 Byte',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testMultipleChunks(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/multi_chunk_source.dat');
      final encryptedFile = File('${tempDir.path}/multi_chunk_encrypted.dat');
      final decryptedFile = File('${tempDir.path}/multi_chunk_decrypted.dat');

      final testData = Uint8List(_chunkSize * 3 + 100);
      for (int i = 0; i < testData.length; i++) {
        testData[i] = (i * 13) % 256;
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
        throw Exception('Multiple chunks file decryption failed');
      }

      _logger.info('Multiple chunks test passed');
      return TestResult(
        name: 'Multiple Chunks (3.1 chunks)',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Multiple chunks test failed', e);
      return TestResult(
        name: 'Multiple Chunks (3.1 chunks)',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testLargeFile(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/large_file_source.dat');
      final encryptedFile = File('${tempDir.path}/large_file_encrypted.dat');
      final decryptedFile = File('${tempDir.path}/large_file_decrypted.dat');

      final sizeMB = 50;
      final testData = Uint8List(sizeMB * 1024 * 1024);

      for (int i = 0; i < testData.length; i += 1024) {
        testData[i] = (i ~/ 1024) % 256;
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
        throw Exception('Large file decryption failed');
      }

      _logger.info('Large file test passed');
      return TestResult(
        name: 'Large File (${sizeMB}MB)',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Large file test failed', e);
      return TestResult(
        name: 'Large File (50MB)',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testStreamingWithVariousDataPatterns(
      Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final patterns = [
        Uint8List.fromList(List.filled(1000, 0)),
        Uint8List.fromList(List.filled(1000, 255)),
        Uint8List.fromList(List.generate(1000, (i) => i % 256)),
        Uint8List.fromList(List.generate(1000, (i) => (i * 7) % 256)),
      ];

      for (int p = 0; p < patterns.length; p++) {
        final sourceFile = File('${tempDir.path}/pattern_${p}_source.dat');
        final encryptedFile =
            File('${tempDir.path}/pattern_${p}_encrypted.dat');
        final decryptedFile =
            File('${tempDir.path}/pattern_${p}_decrypted.dat');

        await sourceFile.writeAsBytes(patterns[p]);

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
          throw Exception('Pattern $p decryption failed');
        }
      }

      _logger.info('Various data patterns test passed');
      return TestResult(
        name: 'Various Data Patterns',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Various data patterns test failed', e);
      return TestResult(
        name: 'Various Data Patterns',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<TestResult> _testConcurrentStreaming(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final futures = <Future>[];

      for (int i = 0; i < 5; i++) {
        futures.add(_encryptDecryptFile(tempDir, i));
      }

      await Future.wait(futures);

      _logger.info('Concurrent streaming test passed');
      return TestResult(
        name: 'Concurrent Streaming (5 files)',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Concurrent streaming test failed', e);
      return TestResult(
        name: 'Concurrent Streaming (5 files)',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<void> _encryptDecryptFile(Directory tempDir, int index) async {
    final sourceFile = File('${tempDir.path}/concurrent_${index}_source.dat');
    final encryptedFile =
        File('${tempDir.path}/concurrent_${index}_encrypted.dat');
    final decryptedFile =
        File('${tempDir.path}/concurrent_${index}_decrypted.dat');

    final testData = Uint8List(1024 * 100);
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
      throw Exception('Concurrent file $index decryption failed');
    }
  }

  Future<TestResult> _testStreamingWithInvalidHeader(Directory tempDir) async {
    final stopwatch = Stopwatch()..start();
    try {
      final sourceFile = File('${tempDir.path}/invalid_header_source.dat');
      final encryptedFile =
          File('${tempDir.path}/invalid_header_encrypted.dat');
      final decryptedFile =
          File('${tempDir.path}/invalid_header_decrypted.dat');

      final testData = Uint8List(1024);
      for (int i = 0; i < testData.length; i++) {
        testData[i] = i % 256;
      }
      await sourceFile.writeAsBytes(testData);

      final encryptResult = await CryptoUtil.encryptFile(
        sourceFile.path,
        encryptedFile.path,
      );

      final invalidHeader = Uint8List(24);

      try {
        await CryptoUtil.decryptFile(
          encryptedFile.path,
          decryptedFile.path,
          invalidHeader,
          encryptResult.key!,
        );
        throw Exception('Should have failed with invalid header');
      } catch (e) {
        _logger.info('Correctly rejected invalid header: $e');
      }

      _logger.info('Invalid header test passed');
      return TestResult(
        name: 'Invalid Header Rejection',
        passed: true,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      _logger.severe('Invalid header test failed', e);
      return TestResult(
        name: 'Invalid Header Rejection',
        passed: false,
        duration: stopwatch.elapsed,
        error: e.toString(),
      );
    }
  }

  Future<void> _cleanupTestFiles(Directory tempDir) async {
    final files = tempDir.listSync();
    for (final file in files) {
      if (file is File && file.path.contains('.dat')) {
        try {
          await file.delete();
        } catch (_) {}
      }
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
