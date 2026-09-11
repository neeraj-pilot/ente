# Ente Cast for tvOS

Source code for the Ente Cast tvOS app.

## Building from source

1. Install [Xcode](https://developer.apple.com/xcode/) and [Rust](https://www.rust-lang.org/tools/install). In Xcode install the tvOS platform.

2. Generate the Swift bindings:

   ```sh
   cd rust
   cargo codegen native cast
   ```

3. Open `Cast.xcodeproj` in Xcode and run the `Cast` scheme.

> [!NOTE]
>
> The first build will install the Rust `nightly` toolchain and `rust-src`. They are needed for building Rust for tvOS targets.
>
> Re-run `cargo codegen native cast` whenever the UniFFI-exported surface of the cast crate (`rust/bindings/uniffi/cast`) changes.

## Releasing

Run `cast-build.yml` manually on `main` to archive and upload a TestFlight build.
Its build number is the committed Cast Release build number plus the GitHub
Actions run number.

For a release candidate, create `release/cast-v<version>` after setting the Cast
Release marketing version to `<version>`, then run the same workflow on that
branch. Release branches use the committed build number; increment it before
uploading another build for the same version.

Before the first release, create the `Cast App Store` provisioning profile for
`io.ente.frame.tv.cast`. The shared certificate and App Store Connect API-key
secrets are documented in [Apple signing](../../../.github/docs/apple-signing.md).
