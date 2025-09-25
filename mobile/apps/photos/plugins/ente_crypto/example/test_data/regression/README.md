# Regression Test Data

This directory contains cryptographic test vectors for cross-platform regression testing.

## Purpose

These test vectors ensure that:
1. Different platforms (Android, iOS, Web) can decrypt data encrypted by other platforms
2. Changes to the crypto implementation don't break backward compatibility
3. Streaming encryption handles chunk boundaries correctly (especially 4MB boundaries)

## Directory Structure

```
regression/
├── droid-photos-v1/      # Android test data with file encryption
│   ├── metadata.json
│   ├── test_vectors.json
│   ├── edge_cases.json
│   └── encrypted_files/  # Binary encrypted test files
│       ├── file_vectors.json
│       └── *.enc files
└── [future platforms]/
```

## Setting Up Test Data

### Local Development

1. Copy test data from your local test repository:
   ```bash
   cp -r ~/test-data/crypto/droid-* test_data/regression/
   ```

2. Run `flutter pub get` to update the asset bundle

### CI/CD Integration

The test data is bundled as Flutter assets in the APK, making it available for:
- Device farm testing (AWS Device Farm, Firebase Test Lab)
- CI integration tests
- Local integration tests

## How It Works

1. **Asset Discovery**: The regression test runner uses `AssetManifest.json` to discover available test platforms
2. **Dynamic Loading**: Test data is loaded from assets using `rootBundle`
3. **Binary Files**: Encrypted files (*.enc) are loaded as binary data via `rootBundle.load()`
4. **SHA256 Verification**: Decrypted files are verified against stored SHA256 hashes

## Adding New Test Data

1. Generate test data on your platform using the test app
2. Pull the data via ADB (Android) or similar methods
3. Copy to this directory following the naming convention: `{platform}-v{version}/`
4. Update this README with the new platform

## File Encryption Tests

The `encrypted_files/` directory contains actual encrypted binary files to test:
- Empty files (0 bytes)
- Small files (100 bytes)
- Medium files (4KB)
- Large files (1MB)
- Files crossing 4MB chunk boundary (4MB + 100 bytes)

Each encrypted file has corresponding metadata in `file_vectors.json` including:
- Encryption key and header
- Original and encrypted SHA256 hashes
- File sizes and chunk information

## Security Note

The test data uses **deterministic keys for testing only**. These keys are:
- NOT secure for production use
- Publicly visible in the test vectors
- Only for verifying cryptographic compatibility

## Integration with Test Runner

The regression test runner (`lib/test_cases/regression_tests.dart`) automatically:
1. Discovers all platforms in this directory
2. Loads test vectors and encrypted files
3. Runs decryption tests
4. Verifies against expected hashes

## Specification

For the complete test vector specification, see: `~/test-data/crypto/SPEC.md`