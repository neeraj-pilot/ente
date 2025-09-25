# Ente Crypto Test Runner

A comprehensive integration testing app for the `ente_crypto` Flutter plugin, implementing a phased testing approach with UI-driven test execution.

## Features

### Phase 1 - UI-Based Testing (Implemented)
- **Visual Test Runner**: Interactive UI to run different test categories
- **Test Categories**:
  - Basic Crypto (XSalsa20 encryption/decryption)
  - Streaming (ChaCha20 with extensive edge cases)
  - Key Derivation (Argon2id variants)
  - File Operations (File encryption/hashing)
- **Real-time Status**: Visual indicators for test progress and results
- **Detailed Results**: Expandable test results with error details
- **Result Export**: Copy test results to clipboard

### Test Coverage

#### Basic Crypto Tests
- Small/large/empty data encryption
- Invalid key/nonce handling
- Base64/Hex encoding
- Key generation uniqueness
- Sealed box encryption
- ChaCha20 small data

#### Streaming Tests (Edge Cases)
- Empty file (0 bytes)
- Single byte file
- Exact chunk boundary (4MB)
- Chunk size - 1 byte
- Chunk size + 1 byte
- Multiple chunks
- Large files (50MB+)
- Various data patterns
- Concurrent operations
- Invalid header rejection

#### Key Derivation Tests
- Sensitive key derivation with memory fallback
- Interactive key derivation
- Login key derivation
- Empty/long passwords
- Deterministic behavior
- Salt uniqueness
- Memory limit handling

#### File Operation Tests
- Basic file encryption/decryption
- File hashing
- Concurrent operations
- Non-existent file handling
- File overwriting
- Special character filenames
- Deep directory structures
- Pre-generated keys

## Usage

### Running the Example App

```bash
# From the example directory
flutter pub get
flutter run -t lib/main.dart
```

### Running Integration Tests

```bash
# Run all integration tests
flutter test integration_test/crypto_integration_test.dart

# Run on a specific device
flutter test integration_test/crypto_integration_test.dart -d <device-id>
```

### Running Individual Test Categories

Tap on any test category in the UI to run those specific tests:
1. Basic Crypto - Core encryption functionality
2. Streaming - File streaming with edge cases
3. Key Derivation - Password-based key generation
4. File Operations - File-based encryption

### Running All Tests

Tap the play button in the app bar to run all test categories sequentially.

## Phase 2 - Test Data Generation (Prepared)

The app includes utilities for generating and verifying test vectors:

### Test Data Generator
Located in `lib/utils/test_data_generator.dart`, provides:
- Generation of comprehensive test vectors
- Saving test data for regression testing
- Loading and verifying historical test data
- Regression test execution

### Extracting Test Data (Android)
Use the provided script to extract test data via ADB:

```bash
./tools/extract_test_data.sh [package_name]
```

This will pull generated test vectors from the device for version control and regression testing.

## Project Structure

```
example/
├── lib/
│   ├── main.dart                    # App entry point
│   ├── screens/
│   │   ├── test_runner_screen.dart  # Main test UI
│   │   └── test_results_screen.dart # Results display
│   ├── test_cases/
│   │   ├── basic_crypto_tests.dart  # Basic encryption tests
│   │   ├── streaming_tests.dart     # Streaming edge cases
│   │   ├── key_derivation_tests.dart# Key derivation tests
│   │   └── file_tests.dart          # File operation tests
│   └── utils/
│       └── test_data_generator.dart # Test vector generation
├── integration_test/
│   └── crypto_integration_test.dart # UI automation tests
├── test_data/                       # Test vectors storage
└── tools/
    └── extract_test_data.sh         # ADB extraction script
```

## Development

### Adding New Tests

1. Add test logic to appropriate test case file in `lib/test_cases/`
2. Implement `TestRunner` interface
3. Register in `TestRunnerScreen._testCategories`
4. Add corresponding integration test

### Generating Test Vectors

```dart
// In your test code
final dataSet = await TestDataGenerator.generateCompleteTestDataSet();
await TestDataGenerator.saveTestDataSet(dataSet);
```

### Running Regression Tests

```dart
final dataSet = await TestDataGenerator.loadTestDataSet('1.0.0');
final report = await TestDataGenerator.runRegressionTests(dataSet);
```

## CI/CD Integration

The tests are designed to be run in CI pipelines:

```yaml
# Example GitHub Actions workflow
- name: Run Crypto Integration Tests
  run: |
    flutter test integration_test/crypto_integration_test.dart \
      --coverage \
      --coverage-path=coverage/lcov.info
```

## Notes

- Requires `Computer.shared()` initialization with 4 workers
- Uses `flutter_sodium` for cryptographic operations
- All file operations use absolute paths
- Tests include memory-constrained scenarios
- Supports concurrent operation testing