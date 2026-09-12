# Android

Use JDK 17 and set `ANDROID_HOME` to your Android SDK. From the repository root:

```sh
# Format
./android/gradlew -p android ktfmtFormat

# Lint
./android/scripts/lint.sh
```

Ensu is currently excluded from formatting and linting.
