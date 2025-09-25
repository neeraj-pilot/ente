import 'package:flutter/material.dart';

import 'package:ente_crypto_example/screens/regression_test_screen.dart';
import 'package:ente_crypto_example/test_cases/basic_crypto_tests.dart';
import 'package:ente_crypto_example/test_cases/file_tests.dart';
import 'package:ente_crypto_example/test_cases/key_derivation_tests.dart';
import 'package:ente_crypto_example/test_cases/streaming_tests.dart';
import 'package:ente_crypto_example/screens/test_results_screen.dart';
import 'package:ente_crypto_example/utils/test_data_exporter.dart';

class TestRunnerScreen extends StatefulWidget {
  const TestRunnerScreen({super.key});

  @override
  State<TestRunnerScreen> createState() => _TestRunnerScreenState();
}

class _TestRunnerScreenState extends State<TestRunnerScreen> {
  final Map<String, TestCategory> _testCategories = {
    'Basic Crypto': TestCategory(
      icon: Icons.lock,
      description: 'XSalsa20 encryption/decryption tests',
      testRunner: BasicCryptoTests(),
    ),
    'Streaming': TestCategory(
      icon: Icons.stream,
      description: 'ChaCha20 streaming encryption with edge cases',
      testRunner: StreamingTests(),
    ),
    'Key Derivation': TestCategory(
      icon: Icons.key,
      description: 'Argon2id key derivation tests',
      testRunner: KeyDerivationTests(),
    ),
    'File Operations': TestCategory(
      icon: Icons.folder,
      description: 'File encryption/decryption tests',
      testRunner: FileTests(),
    ),
  };

  final Map<String, TestStatus> _testStatuses = {};
  bool _isRunningAll = false;

  @override
  void initState() {
    super.initState();
    for (final category in _testCategories.keys) {
      _testStatuses[category] = TestStatus.notStarted;
    }
  }

  Future<void> _runTests(String category) async {
    setState(() {
      _testStatuses[category] = TestStatus.running;
    });

    try {
      final testRunner = _testCategories[category]!.testRunner;
      final results = await testRunner.runTests();

      setState(() {
        _testStatuses[category] =
            results.failedCount == 0 ? TestStatus.passed : TestStatus.failed;
      });

      if (mounted) {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => TestResultsScreen(
              categoryName: category,
              results: results,
            ),
          ),
        );
      }
    } catch (e) {
      setState(() {
        _testStatuses[category] = TestStatus.failed;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error running $category tests: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _runAllTests() async {
    setState(() {
      _isRunningAll = true;
      for (final category in _testCategories.keys) {
        _testStatuses[category] = TestStatus.notStarted;
      }
    });

    final allResults = <String, TestResults>{};

    for (final category in _testCategories.keys) {
      setState(() {
        _testStatuses[category] = TestStatus.running;
      });

      try {
        final testRunner = _testCategories[category]!.testRunner;
        final results = await testRunner.runTests();
        allResults[category] = results;

        setState(() {
          _testStatuses[category] =
              results.failedCount == 0 ? TestStatus.passed : TestStatus.failed;
        });
      } catch (e) {
        setState(() {
          _testStatuses[category] = TestStatus.failed;
        });
      }
    }

    setState(() {
      _isRunningAll = false;
    });

    if (mounted && allResults.isNotEmpty) {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => TestResultsScreen(
            categoryName: 'All Tests',
            results: _combineResults(allResults),
          ),
        ),
      );
    }
  }

  TestResults _combineResults(Map<String, TestResults> allResults) {
    final combinedResults = TestResults();

    for (final entry in allResults.entries) {
      for (final result in entry.value.results) {
        combinedResults.addResult(
          TestResult(
            name: '${entry.key}: ${result.name}',
            passed: result.passed,
            duration: result.duration,
            error: result.error,
          ),
        );
      }
    }

    return combinedResults;
  }

  Future<void> _generateTestData() async {
    // Show dialog to get folder name
    final defaultName = 'droid-v${DateTime.now().millisecondsSinceEpoch ~/ 1000}';
    final controller = TextEditingController(text: defaultName);

    final folderName = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Generate Test Data'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Enter a name for the test data set:'),
            const SizedBox(height: 16),
            TextField(
              controller: controller,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Folder name',
                hintText: 'e.g., droid-v1.0',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Will be saved to: cache/current/<folder-name>/',
              style: TextStyle(fontSize: 12, color: Colors.grey[600]),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              final name = controller.text.trim();
              if (name.isNotEmpty) {
                Navigator.pop(context, name);
              }
            },
            child: const Text('Generate'),
          ),
        ],
      ),
    );

    if (folderName == null || folderName.isEmpty) {
      return;
    }

    // Show progress dialog
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: const Text('Generating Test Data'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text('Generating test vectors in $folderName...'),
          ],
        ),
      ),
    );

    try {
      await TestDataExporter.exportTestDataToAppCache(folderName: folderName);

      if (mounted) {
        Navigator.pop(context); // Close progress dialog

        // Show success dialog with path
        final cachePath = await TestDataExporter.getAppCachePath();
        showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Success'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Test data generated successfully!'),
                const SizedBox(height: 8),
                const Text('Location:', style: TextStyle(fontWeight: FontWeight.bold)),
                Text(cachePath, style: const TextStyle(fontSize: 12)),
                const SizedBox(height: 8),
                const Text('You can pull the data using ADB:',
                  style: TextStyle(fontWeight: FontWeight.bold)),
                Text('adb pull $cachePath/current/$folderName/',
                  style: const TextStyle(fontSize: 12, fontFamily: 'monospace')),
                const SizedBox(height: 8),
                const Text('To push data from other platforms:',
                  style: TextStyle(fontWeight: FontWeight.bold)),
                Text('adb push ~/test-data/ios-v1.0/ $cachePath/existing/',
                  style: const TextStyle(fontSize: 12, fontFamily: 'monospace')),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('OK'),
              ),
            ],
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        Navigator.pop(context); // Close progress dialog

        // Show error dialog
        showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Error'),
            content: Text('Failed to generate test data: $e'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('OK'),
              ),
            ],
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Ente Crypto Test Runner'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          IconButton(
            icon: const Icon(Icons.play_arrow),
            onPressed: _isRunningAll ? null : _runAllTests,
            tooltip: 'Run All Tests',
          ),
          PopupMenuButton<String>(
            onSelected: (value) async {
              if (value == 'generate_test_data') {
                await _generateTestData();
              }
            },
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: 'generate_test_data',
                child: Row(
                  children: [
                    Icon(Icons.save_alt, size: 20),
                    SizedBox(width: 8),
                    Text('Generate Test Data'),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
      body: Column(
        children: [
          // Regression Test Card
          Card(
            margin: const EdgeInsets.all(16),
            color: Theme.of(context).colorScheme.primaryContainer,
            child: InkWell(
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const RegressionTestScreen(),
                  ),
                );
              },
              borderRadius: BorderRadius.circular(12),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Icon(
                      Icons.history,
                      size: 40,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Regression Tests',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Generate and validate test vectors for cross-platform compatibility',
                            style: TextStyle(
                              fontSize: 14,
                              color: Theme.of(context).colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Icon(
                      Icons.arrow_forward_ios,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ],
                ),
              ),
            ),
          ),
          const Divider(indent: 16, endIndent: 16),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: _testCategories.length,
              itemBuilder: (context, index) {
          final category = _testCategories.keys.elementAt(index);
          final testInfo = _testCategories[category]!;
          final status = _testStatuses[category]!;

          return Card(
            margin: const EdgeInsets.only(bottom: 12),
            elevation: status == TestStatus.running ? 4 : 1,
            child: ListTile(
              leading: CircleAvatar(
                backgroundColor: _getStatusColor(status).withOpacity(0.2),
                child: Icon(
                  testInfo.icon,
                  color: _getStatusColor(status),
                ),
              ),
              title: Text(
                category,
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              subtitle: Text(testInfo.description),
              trailing: _buildTrailing(status, category),
              onTap: status == TestStatus.running || _isRunningAll
                  ? null
                  : () => _runTests(category),
            ),
          );
        },
      ),
          ),
          Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.1),
              blurRadius: 4,
              offset: const Offset(0, -2),
            ),
          ],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _buildStatusIndicator(TestStatus.passed, 'Passed'),
            _buildStatusIndicator(TestStatus.failed, 'Failed'),
            _buildStatusIndicator(TestStatus.running, 'Running'),
            _buildStatusIndicator(TestStatus.notStarted, 'Not Started'),
          ],
        ),
      ),
        ],
      ),
    );
  }

  Widget _buildTrailing(TestStatus status, String category) {
    switch (status) {
      case TestStatus.running:
        return const SizedBox(
          width: 24,
          height: 24,
          child: CircularProgressIndicator(strokeWidth: 2),
        );
      case TestStatus.passed:
        return Icon(Icons.check_circle, color: Colors.green[700]);
      case TestStatus.failed:
        return Icon(Icons.error, color: Colors.red[700]);
      case TestStatus.notStarted:
        return Icon(Icons.play_circle_outline, color: Colors.grey[600]);
    }
  }

  Widget _buildStatusIndicator(TestStatus status, String label) {
    final count = _testStatuses.values.where((s) => s == status).length;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 32,
          height: 32,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: _getStatusColor(status).withOpacity(0.2),
          ),
          child: Center(
            child: Text(
              count.toString(),
              style: TextStyle(
                color: _getStatusColor(status),
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: TextStyle(fontSize: 12, color: Colors.grey[600]),
        ),
      ],
    );
  }

  Color _getStatusColor(TestStatus status) {
    switch (status) {
      case TestStatus.notStarted:
        return Colors.grey;
      case TestStatus.running:
        return Colors.blue;
      case TestStatus.passed:
        return Colors.green;
      case TestStatus.failed:
        return Colors.red;
    }
  }
}

enum TestStatus {
  notStarted,
  running,
  passed,
  failed,
}

class TestCategory {
  final IconData icon;
  final String description;
  final TestRunner testRunner;

  TestCategory({
    required this.icon,
    required this.description,
    required this.testRunner,
  });
}

abstract class TestRunner {
  Future<TestResults> runTests();
}

class TestResults {
  final List<TestResult> results = [];

  int get totalCount => results.length;
  int get passedCount => results.where((r) => r.passed).length;
  int get failedCount => results.where((r) => !r.passed).length;

  void addResult(TestResult result) {
    results.add(result);
  }
}

class TestResult {
  final String name;
  final bool passed;
  final Duration duration;
  final String? error;

  TestResult({
    required this.name,
    required this.passed,
    required this.duration,
    this.error,
  });
}
