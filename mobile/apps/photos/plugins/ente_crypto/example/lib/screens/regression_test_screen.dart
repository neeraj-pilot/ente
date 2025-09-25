import 'dart:convert';

import 'package:ente_crypto_example/test_cases/regression_tests.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:logging/logging.dart';

class RegressionTestScreen extends StatefulWidget {
  const RegressionTestScreen({super.key});

  @override
  State<RegressionTestScreen> createState() => _RegressionTestScreenState();
}

class _RegressionTestScreenState extends State<RegressionTestScreen> {
  static final _logger = Logger('RegressionTestScreen');
  bool _isLoading = true;
  List<TestDataInfo> _availableTestData = [];
  TestDataInfo? _selectedTestData;
  bool _isRunningTests = false;

  Map<String, dynamic>? _metadata;
  List<RegressionTestResult> _testResults = [];
  String? _error;

  @override
  void initState() {
    super.initState();
    _checkTestData();
  }

  Future<void> _checkTestData() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      // Discover all available test data in app cache
      final testDataList = await RegressionTestRunner.discoverTestData();

      setState(() {
        _availableTestData = testDataList;
        if (testDataList.isNotEmpty) {
          _selectedTestData = testDataList.first;
          _loadMetadata(_selectedTestData!);
        }
      });
    } catch (e) {
      setState(() {
        _error = 'Failed to discover test data: $e';
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _loadMetadata(TestDataInfo testData) async {
    try {
      // Load metadata from assets
      final metadataContent = await rootBundle.loadString(
        'test_data/regression/${testData.path}/metadata.json'
      );
      final metadata = jsonDecode(metadataContent);
      setState(() {
        _metadata = metadata;
      });
    } catch (e) {
      _logger.warning('Failed to load metadata: $e');
    }
  }


  Future<void> _runRegressionTests() async {
    if (_selectedTestData == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please select test data first'),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    setState(() {
      _isRunningTests = true;
      _testResults = [];
      _error = null;
    });

    try {
      // Create runner from bundled assets
      final runner = await RegressionTestRunner.createFromAssets(_selectedTestData!.path);
      if (runner == null) {
        throw Exception('Failed to create test runner');
      }
      final results = await runner.runTests();

      setState(() {
        _testResults = results;
      });
    } catch (e) {
      setState(() {
        _error = 'Failed to run regression tests: $e';
      });
    } finally {
      setState(() {
        _isRunningTests = false;
      });
    }
  }

  void _copyResults() {
    final buffer = StringBuffer();
    buffer.writeln('Regression Test Results');
    buffer.writeln('=' * 40);

    if (_metadata != null) {
      buffer.writeln('Platform: ${_metadata!['platform']}');
      buffer.writeln('Version: ${_metadata!['version']}');
      buffer.writeln('Generated: ${_metadata!['timestamp']}');
      buffer.writeln('=' * 40);
    }

    final passed = _testResults.where((r) => r.passed).length;
    final failed = _testResults.where((r) => !r.passed).length;
    buffer.writeln('Total: ${_testResults.length}');
    buffer.writeln('Passed: $passed');
    buffer.writeln('Failed: $failed');
    buffer.writeln('=' * 40);

    for (final result in _testResults) {
      buffer.writeln(
        '${result.passed ? "✓" : "✗"} ${result.testId}: ${result.description}',
      );
      if (!result.passed && result.error != null) {
        buffer.writeln('  Error: ${result.error}');
      }
    }

    Clipboard.setData(ClipboardData(text: buffer.toString()));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Results copied to clipboard')),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Regression Tests'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          if (_testResults.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.copy),
              onPressed: _copyResults,
              tooltip: 'Copy Results',
            ),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return _buildErrorView();
    }

    if (_availableTestData.isEmpty) {
      return _buildNoDataView();
    }

    return _buildTestView();
  }

  Widget _buildErrorView() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error, size: 64, color: Colors.red[700]),
            const SizedBox(height: 16),
            Text(
              'Error',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 8),
            Text(
              _error!,
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.red[700]),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _checkTestData,
              icon: const Icon(Icons.refresh),
              label: const Text('Retry'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNoDataView() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.folder_off, size: 64, color: Colors.orange[700]),
            const SizedBox(height: 16),
            Text(
              'No Test Data Found',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 8),
            const Text(
              'No test data found in assets. Please add test data to test_data/regression/ directory.',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _checkTestData,
              icon: const Icon(Icons.refresh),
              label: const Text('Refresh'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTestView() {
    return Column(
      children: [
        _buildMetadataCard(),
        if (_testResults.isEmpty) _buildRunTestsCard(),
        if (_testResults.isNotEmpty) _buildResultsSummary(),
        if (_testResults.isNotEmpty)
          Expanded(
            child: _buildResultsList(),
          ),
      ],
    );
  }

  Widget _buildMetadataCard() {

    return Card(
      margin: const EdgeInsets.all(16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Test Data',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                if (_availableTestData.length > 1)
                  DropdownButton<TestDataInfo>(
                    value: _selectedTestData,
                    items: _availableTestData.map((data) =>
                      DropdownMenuItem(
                        value: data,
                        child: Text(data.displayName),
                      ),
                    ).toList(),
                    onChanged: (value) {
                      if (value != null) {
                        setState(() {
                          _selectedTestData = value;
                          _testResults = [];
                        });
                        _loadMetadata(value);
                      }
                    },
                  ),
              ],
            ),
            const SizedBox(height: 8),
            if (_metadata != null) ...[
              _buildInfoRow('Platform', _metadata!['platform']),
              _buildInfoRow('Version', _metadata!['version']),
              _buildInfoRow('Generated', _formatTimestamp(_metadata!['timestamp'])),
              _buildInfoRow('Test Count', _selectedTestData?.testCount ?? 0),
              if (_metadata!['generator'] != null) ...[
                _buildInfoRow('Library', _metadata!['generator']['library']),
                _buildInfoRow('Language', _metadata!['generator']['language']),
              ],
            ],
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _checkTestData,
              icon: const Icon(Icons.refresh),
              label: const Text('Refresh'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRunTestsCard() {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            const Icon(Icons.play_arrow, size: 48, color: Colors.green),
            const SizedBox(height: 16),
            const Text(
              'Ready to run regression tests',
              style: TextStyle(fontSize: 16),
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              onPressed: _isRunningTests ? null : _runRegressionTests,
              icon: _isRunningTests
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.play_arrow),
              label: Text(_isRunningTests ? 'Running Tests...' : 'Run Tests'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResultsSummary() {
    final passed = _testResults.where((r) => r.passed).length;
    final failed = _testResults.where((r) => !r.passed).length;
    final passRate = _testResults.isEmpty
        ? 0.0
        : (passed / _testResults.length) * 100;

    return Card(
      margin: const EdgeInsets.all(16),
      color: failed > 0 ? Colors.red[50] : Colors.green[50],
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _buildSummaryItem('Total', _testResults.length, Icons.list),
            _buildSummaryItem('Passed', passed, Icons.check_circle, Colors.green),
            _buildSummaryItem('Failed', failed, Icons.error, Colors.red),
            _buildSummaryItem('Pass Rate', '${passRate.toStringAsFixed(1)}%', Icons.percent),
          ],
        ),
      ),
    );
  }

  Widget _buildSummaryItem(String label, dynamic value, IconData icon, [Color? color]) {
    return Column(
      children: [
        Icon(icon, color: color ?? Colors.blue, size: 24),
        const SizedBox(height: 4),
        Text(
          value.toString(),
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
        Text(
          label,
          style: const TextStyle(fontSize: 12, color: Colors.grey),
        ),
      ],
    );
  }

  Widget _buildResultsList() {
    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      itemCount: _testResults.length,
      itemBuilder: (context, index) {
        final result = _testResults[index];
        return Card(
          margin: const EdgeInsets.only(bottom: 8),
          child: ListTile(
            leading: Icon(
              result.passed ? Icons.check_circle : Icons.error,
              color: result.passed ? Colors.green : Colors.red,
            ),
            title: Text(result.testId),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(result.description),
                if (!result.passed && result.error != null)
                  Text(
                    'Error: ${result.error}',
                    style: TextStyle(color: Colors.red[700], fontSize: 12),
                  ),
              ],
            ),
            isThreeLine: !result.passed,
          ),
        );
      },
    );
  }

  Widget _buildInfoRow(String label, dynamic value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 100,
            child: Text(
              '$label:',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
          ),
          Expanded(
            child: Text(value.toString()),
          ),
        ],
      ),
    );
  }

  String _formatTimestamp(String timestamp) {
    try {
      final dt = DateTime.parse(timestamp);
      return '${dt.day}/${dt.month}/${dt.year} ${dt.hour}:${dt.minute.toString().padLeft(2, '0')}';
    } catch (_) {
      return timestamp;
    }
  }

}