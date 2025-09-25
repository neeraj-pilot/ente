#!/bin/bash

# Script to extract test data from Android device via ADB
# Usage: ./extract_test_data.sh [package_name]

PACKAGE_NAME=${1:-"com.example.ente_crypto_example"}
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_DIR="./test_data_export_${TIMESTAMP}"

echo "Extracting test data from package: $PACKAGE_NAME"
echo "Output directory: $OUTPUT_DIR"

mkdir -p "$OUTPUT_DIR"

# Pull test data from app's document directory
echo "Pulling test data..."
adb shell "run-as $PACKAGE_NAME ls /data/data/$PACKAGE_NAME/app_flutter/" 2>/dev/null
adb shell "run-as $PACKAGE_NAME tar -cf - /data/data/$PACKAGE_NAME/app_flutter/test_data 2>/dev/null" | tar -xf - -C "$OUTPUT_DIR" 2>/dev/null

# Check if extraction was successful
if [ -d "$OUTPUT_DIR/data" ]; then
    echo "Test data extracted successfully!"

    # Organize the extracted data
    mv "$OUTPUT_DIR/data/data/$PACKAGE_NAME/app_flutter/test_data" "$OUTPUT_DIR/test_data"
    rm -rf "$OUTPUT_DIR/data"

    # List extracted files
    echo ""
    echo "Extracted files:"
    find "$OUTPUT_DIR/test_data" -type f -name "*.json" | while read file; do
        echo "  - $file"
    done
else
    echo "Failed to extract test data. Make sure:"
    echo "  1. Device is connected (adb devices)"
    echo "  2. App is installed"
    echo "  3. Test data has been generated"
fi

echo ""
echo "To use these test vectors for regression testing:"
echo "  1. Copy the test_data folder to plugins/ente_crypto/example/"
echo "  2. Run regression tests in the app"