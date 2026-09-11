# Apple

Native Apple apps and shared packages.

## Development

Use Xcode 26.2. From the repository root:

```sh
# Format
swift run -c release --package-path apple swiftformat apple

# Lint
./apple/scripts/lint.sh
```

Swift Package Manager builds the pinned SwiftFormat on first run. Both commands use `.swiftformat`,
which sets a 100-column width and initially excludes Ensu and generated code. CI runs the lint command.
