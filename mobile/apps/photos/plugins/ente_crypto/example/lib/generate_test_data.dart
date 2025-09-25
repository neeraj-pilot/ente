import 'package:ente_crypto/ente_crypto.dart';
import 'package:ente_crypto_example/utils/test_data_exporter.dart';
import 'package:logging/logging.dart';

void main() async {
  // Setup logging
  Logger.root.level = Level.ALL;
  Logger.root.onRecord.listen((record) {
    print('${record.level.name}: ${record.time}: ${record.message}');
  });

  print('Initializing CryptoUtil...');
  CryptoUtil.init();

  print('Generating test data...');
  try {
    await TestDataExporter.exportTestData();
    print('Test data generation completed successfully!');
    print('Test data saved to: /Users/duckydev/test-data/crypto/droid-v1.0/');
  } catch (e, s) {
    print('Failed to generate test data: $e');
    print('Stack trace: $s');
  }
}