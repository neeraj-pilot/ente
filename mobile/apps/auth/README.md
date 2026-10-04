# Ente Auth

Ente's 2FA app. An end-to-end encrypted, cross platform and free app for storing your 2FA codes with cloud backups. Works offline. You can even use it without signing up for an account if you don't want the cloud backups or multi-device sync.

![App Screenshots](./docs/assets/screenshots.png)

## 📲 Download

### Android

This repository's [GitHub releases](https://github.com/ente/ente/releases?q=tag%3Aauth-v4) contains APKs, built straight from source. These builds keep themselves updated, without relying on third party stores.

You can alternatively install the build from PlayStore or F-Droid.

<a href="https://play.google.com/store/apps/details?id=io.ente.auth">
  <img height="59" src="../../../.github/assets/play-store-badge.png">
</a>
<a href="https://f-droid.org/packages/io.ente.auth/">
  <img height="59" src="../../../.github/assets/f-droid-badge.png">
</a>

### iOS / Apple Silicon macOS

<a href="https://apps.apple.com/us/app/ente-authenticator/id6444121398">
  <img height="59" src="../../../.github/assets/app-store-badge.svg">
</a>

### Desktop

You can [**download**](https://github.com/ente/ente/releases?q=tag%3Aauth-v4) a native desktop app from this repository's GitHub releases. The desktop app works on Windows, Linux and macOS.

### Web

You can view your 2FA codes at [auth.ente.com](https://auth.ente.com). For adding or managing your secrets, please use our mobile or desktop app.

## 🧑‍💻 Build from source

1. [Install Flutter v3.47.2](https://flutter.dev/docs/get-started/install).

2. Pull in submodules with `git submodule update --init --recursive`

3. From any folder inside `mobile/`, install the workspace dependencies with `flutter pub get --enforce-lockfile`.

4. Run the app:
   - Android: `flutter run --flavor independent`
   - iOS: `flutter run`
   - macOS: `flutter run -d macos`

To build a release APK, [setup your keystore](https://docs.flutter.dev/deployment/android#create-an-upload-keystore) and run `flutter build apk --release --flavor independent`. For iOS, use `flutter build ios`.

### Updating dependencies

After updating Flutter dependencies, run `pod install` from `ios/` on macOS and commit `ios/Podfile.lock` if it changes.

## 🔩 Architecture

The architecture that powers end-to-end encrypted storage and sync of your tokens has been documented [here](../../../architecture/README.md#token-encryption).

### Watch companions

Watch sharing is opt-in for each TOTP or Steam entry. The phone is the only editor.
It sends full encrypted snapshots: additions and edits replace the prior values,
and absent entries are deleted. HOTP is excluded because two offline generators
cannot safely share a mutable counter. Watches generate codes offline from their
own clock. Open both apps after changing selected accounts to sync.

The Flutter service owns consent and exports only while the app is unlocked and
in the foreground. `ios/AuthNative` and `android/auth-native` own enrollment,
encryption, storage, receipts, and platform transport. Flutter adapters do not
own the vault. Each enrollment has a new key, and snapshots bind the enrollment,
protocol version, and monotonically increasing revision. A receipt is sent only
after an authenticated snapshot is stored. Corrupt watch storage requires an
explicit erase and new enrollment; it cannot reset the revision under an old key.

Apple Watch uses WatchConnectivity and CryptoKit HPKE. The feature requires
iOS 17/watchOS 10; the phone app keeps its existing minimum OS version. New pairs
use hybrid X-Wing on iOS/watchOS 26, or P-256 on older supported systems. The watch
key uses `WhenPasscodeSetThisDeviceOnly` Keychain accessibility.

Wear OS uses the Data Layer, X25519 HPKE through Tink, and optional X-Wing only
after a successful provider probe on both devices. The chosen suite is fixed
for that enrollment. The watch requires Android 11/API 30, a screen lock, and
hardware-backed Android Keystore. Its wrapping key requires an unlocked device
and device-credential authentication within 36 hours. If authentication expires,
lock and unlock the watch. The phone adapter requires Android 8/API 26.
One Wear watch can be enrolled at a time; stop sharing before replacing it.
An open setup request from another connected watch pauses new exports to the
current watch while it is disconnected. Reconnect the current watch and retry
from the phone to resume its updates, or finish replacing it.

Both watches exclude their local state from backup. The paired-app channel
authenticates the sender; HPKE base mode alone does not. No Ente login key is
copied to the watch. Logout queues an empty snapshot and invalidates outstanding
vault exports. Stopping sharing retires the watch key. An offline watch cannot
be erased remotely until it reconnects. Losing a watch therefore requires
rotating the affected services' two-factor secrets.
On Apple, an inactive paired watch receives its logout reset only after Auth runs
again with that watch active. Reinstall and phone-transfer flows also need to
exchange fresh enrollment status; they do not provide an immediate remote wipe.

Only the `playstore` Android flavor depends on the watch module. Missing or
outdated Play Services makes watch sync unavailable without preventing phone
startup. Build `fdroid` and `independent` with the existing
`--dart-define=cronetHttpNoPlay=true` option to exclude Cronet's Play dependency
as well. Their existing open-source Tink dependency from secure storage remains;
they do not include the watch module or its newer Tink version. Downstream
F-Droid's recipe must add `mobile/apps/auth/wear` to `rm` and remove the unused
Wear SDK declaration during `prebuild`, alongside its existing Cronet removal:

```yaml
- sed -i -e '/play-services-wearable/d' android/auth-native/build.gradle
```

The unused native module remains configured but is not a dependency of the
F-Droid app. This recipe change needs downstream review; APK exclusion alone
does not prove acceptance by the full F-Droid build and source scanner.

From this directory, build the companions with:

```sh
flutter build ios --simulator --debug
cd wear
./gradlew :app:assembleDebug
```

The Wear project has its own wrapper and reads the version from Auth's pubspec.
Its version code is `1_000_000_000 + phoneBuildNumber`, reserving a separate Play
range. Release builds use the phone's signing key; package names and signing
identities must match for Data Layer delivery. CI saves the signed Wear AAB for
the separate Wear OS track. Set up that track and confirm the reserved version
range in Play Console before publishing. Apple releases also need the watch
App ID and provisioning profile described in
[Apple signing](../../../.github/docs/apple-signing.md).

Before release, verify enrollment, offline add/update/delete reconciliation, credential
expiry, wrist removal, passcode removal, phone/watch restore and transfer, and
background delivery on real Apple Watch and Wear OS hardware. A simulator cannot
prove hardware key protection or an OEM's lock behavior.

## 🌍 Translate

[![Crowdin](https://badges.crowdin.net/ente-photos-app/localized.svg)](https://crowdin.com/project/ente-photos-app)

If you're interested in helping out with translation, please visit our [Crowdin project](https://crowdin.com/project/ente-photos-app). For more details, see the docs for the [strings package](../../packages/strings/README.md).

## 🧑‍🎨 Icons

Ente Auth supports the icon pack provided by [simple-icons](https://github.com/simple-icons/simple-icons). If you wish to add more, see [docs/adding-icons](docs/adding-icons.md).

## 💚 Contribute

The best way to support this project is by checking out [Ente Photos](../mobile/README.md) or spreading the word.

For more ways to contribute, see [CONTRIBUTING.md](../../../CONTRIBUTING.md).

## ⭐️ About

To know more about Ente and the ways to get in touch or seek help, see [our main README](../../../README.md) or visit [ente.com](https://ente.com).
