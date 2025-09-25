import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:ente_crypto_example/screens/test_runner_screen.dart';

class TestResultsScreen extends StatelessWidget {
  final String categoryName;
  final TestResults results;

  const TestResultsScreen({
    super.key,
    required this.categoryName,
    required this.results,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('$categoryName Results'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          IconButton(
            icon: const Icon(Icons.copy),
            onPressed: () => _copyResults(context),
            tooltip: 'Copy Results',
          ),
        ],
      ),
      body: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            color: _getOverallColor().withOpacity(0.1),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _buildSummaryCard(
                  'Total',
                  results.totalCount.toString(),
                  Icons.list,
                  Colors.blue,
                ),
                _buildSummaryCard(
                  'Passed',
                  results.passedCount.toString(),
                  Icons.check_circle,
                  Colors.green,
                ),
                _buildSummaryCard(
                  'Failed',
                  results.failedCount.toString(),
                  Icons.error,
                  Colors.red,
                ),
              ],
            ),
          ),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: results.results.length,
              itemBuilder: (context, index) {
                final result = results.results[index];
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: ExpansionTile(
                    leading: Icon(
                      result.passed ? Icons.check_circle : Icons.error,
                      color: result.passed ? Colors.green : Colors.red,
                      size: 20,
                    ),
                    title: Text(
                      result.name,
                      style: const TextStyle(fontSize: 14),
                    ),
                    subtitle: Text(
                      'Duration: ${result.duration.inMilliseconds}ms',
                      style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                    ),
                    children: result.error != null
                        ? [
                            Container(
                              padding: const EdgeInsets.all(16),
                              color: Colors.red[50],
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text(
                                    'Error:',
                                    style: TextStyle(
                                      fontWeight: FontWeight.bold,
                                      color: Colors.red,
                                    ),
                                  ),
                                  const SizedBox(height: 8),
                                  SelectableText(
                                    result.error!,
                                    style: TextStyle(
                                      fontSize: 12,
                                      fontFamily: 'monospace',
                                      color: Colors.red[900],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ]
                        : [],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSummaryCard(
    String label,
    String value,
    IconData icon,
    Color color,
  ) {
    return Column(
      children: [
        Icon(icon, color: color, size: 32),
        const SizedBox(height: 8),
        Text(
          value,
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
        Text(
          label,
          style: TextStyle(
            fontSize: 12,
            color: Colors.grey[600],
          ),
        ),
      ],
    );
  }

  Color _getOverallColor() {
    if (results.failedCount == 0) {
      return Colors.green;
    } else if (results.passedCount == 0) {
      return Colors.red;
    } else {
      return Colors.orange;
    }
  }

  void _copyResults(BuildContext context) {
    final buffer = StringBuffer();
    buffer.writeln('Test Results: $categoryName');
    buffer.writeln('=' * 40);
    buffer.writeln('Total: ${results.totalCount}');
    buffer.writeln('Passed: ${results.passedCount}');
    buffer.writeln('Failed: ${results.failedCount}');
    buffer.writeln('=' * 40);

    for (final result in results.results) {
      buffer.writeln(
        '${result.passed ? "✓" : "✗"} ${result.name} (${result.duration.inMilliseconds}ms)',
      );
      if (result.error != null) {
        buffer.writeln('  Error: ${result.error}');
      }
    }

    Clipboard.setData(ClipboardData(text: buffer.toString()));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Results copied to clipboard')),
    );
  }
}
