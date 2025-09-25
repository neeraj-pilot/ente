#!/bin/bash

# Setup script for regression test data
# This script copies test data from ~/test-data/crypto to the Flutter assets directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DATA_SOURCE="$HOME/test-data/crypto"
TEST_DATA_DEST="$SCRIPT_DIR/test_data/regression"

echo "Setting up regression test data for ente_crypto..."
echo "================================================"

# Check if source directory exists
if [ ! -d "$TEST_DATA_SOURCE" ]; then
    echo "❌ Error: Test data source not found at $TEST_DATA_SOURCE"
    echo ""
    echo "Please ensure you have test data in ~/test-data/crypto/"
    echo "You can generate test data by:"
    echo "  1. Running the example app on Android"
    echo "  2. Using the overflow menu to 'Generate Test Data'"
    echo "  3. Using ADB to pull the data:"
    echo "     adb pull /data/data/com.example.ente_crypto_example/cache/current/droid-photos-v1 ~/test-data/crypto/"
    exit 1
fi

# Create destination directory if it doesn't exist
mkdir -p "$TEST_DATA_DEST"

# Find all platform directories in source
echo ""
echo "Available test data platforms:"
for platform_dir in "$TEST_DATA_SOURCE"/*; do
    if [ -d "$platform_dir" ]; then
        platform_name=$(basename "$platform_dir")
        echo "  • $platform_name"
    fi
done

echo ""
echo "Copying test data to assets..."

# Copy all platform directories
for platform_dir in "$TEST_DATA_SOURCE"/*; do
    if [ -d "$platform_dir" ]; then
        platform_name=$(basename "$platform_dir")

        # Skip non-platform directories like .git
        if [[ "$platform_name" == .* ]]; then
            continue
        fi

        # Check if it contains test data files
        if [ -f "$platform_dir/metadata.json" ] && [ -f "$platform_dir/test_vectors.json" ]; then
            echo "  ✓ Copying $platform_name..."
            cp -r "$platform_dir" "$TEST_DATA_DEST/"
        else
            echo "  ⚠️  Skipping $platform_name (missing required files)"
        fi
    fi
done

# Verify the copy
echo ""
echo "Verifying test data in assets:"
for platform_dir in "$TEST_DATA_DEST"/*; do
    if [ -d "$platform_dir" ]; then
        platform_name=$(basename "$platform_dir")

        # Skip README and .gitignore
        if [[ "$platform_name" == "README.md" ]] || [[ "$platform_name" == ".gitignore" ]]; then
            continue
        fi

        # Check for required files
        if [ -f "$platform_dir/metadata.json" ] && [ -f "$platform_dir/test_vectors.json" ]; then
            # Count test files
            file_count=$(find "$platform_dir" -type f | wc -l)
            echo "  ✓ $platform_name ($file_count files)"

            # Check for encrypted files
            if [ -d "$platform_dir/encrypted_files" ]; then
                enc_count=$(find "$platform_dir/encrypted_files" -name "*.enc" | wc -l)
                if [ "$enc_count" -gt 0 ]; then
                    echo "    • $enc_count encrypted test files"
                fi
            fi
        fi
    fi
done

echo ""
echo "✅ Test data setup complete!"
echo ""
echo "Next steps:"
echo "  1. Run 'flutter pub get' to update the asset bundle"
echo "  2. Run the app and navigate to 'Regression Tests'"
echo "  3. The test data will be automatically discovered from assets"
echo ""
echo "For CI/Device Farm:"
echo "  • Test data is now bundled with the APK/IPA"
echo "  • No additional setup required on test devices"
echo "  • Tests will run using the bundled data"