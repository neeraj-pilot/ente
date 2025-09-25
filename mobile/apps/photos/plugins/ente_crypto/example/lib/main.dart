import 'package:computer/computer.dart';
import 'package:ente_crypto/ente_crypto.dart';
import 'package:flutter/material.dart';
import 'package:logging/logging.dart';

import 'package:ente_crypto_example/screens/test_runner_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  Logger.root.level = Level.ALL;
  Logger.root.onRecord.listen((record) {
    debugPrint('[${record.level.name}] ${record.time}: ${record.message}');
  });

  await Computer.shared().turnOn(workersCount: 4);
  CryptoUtil.init();

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Ente Crypto Test Runner',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.green),
        useMaterial3: true,
      ),
      home: const TestRunnerScreen(),
    );
  }
}
