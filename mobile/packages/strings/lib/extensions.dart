import 'package:ente_strings/l10n/strings_localizations.dart';
import 'package:ente_strings/l10n/strings_localizations_en.dart';
import 'package:flutter/widgets.dart';

// Re-export the localizations for convenience
export 'l10n/strings_localizations.dart';

final _fallbackStringsLocalizations = StringsLocalizationsEn();

/// Extension to easily access shared strings from any BuildContext
extension EnteStringsExtension on BuildContext {
  /// Get the shared strings localizations for the current locale
  StringsLocalizations get strings =>
      Localizations.of<StringsLocalizations>(this, StringsLocalizations) ??
      _fallbackStringsLocalizations;
}
