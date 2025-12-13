import "package:flutter/foundation.dart";

const bool _localDemoFlag = bool.fromEnvironment(
  "IS_LOCAL_ONLY_DEMO",
  defaultValue: false,
);

const bool _legacyLocalOnlyFlag = bool.fromEnvironment(
  "LOCAL_ONLY",
  defaultValue: false,
);

/// Whether the build should run fully locally without any remote backend calls.
bool get isLocalOnlyDemo => _localDemoFlag || _legacyLocalOnlyFlag;
