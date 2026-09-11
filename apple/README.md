# Apple

Native Apple apps and shared packages.

## Development

Use Xcode 26.2. From this directory:

```sh
make format # Format and apply lint fixes
make lint   # Check formatting and lint rules
```

Formatting uses Xcode's swift-format. Swift Package Manager installs the pinned SwiftLint on first run.
Both commands use the source list in `Makefile`, which initially excludes Ensu and generated code.
