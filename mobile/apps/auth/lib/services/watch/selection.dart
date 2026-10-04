import 'dart:convert';

// Only local IDs and random wire IDs belong here. Never store labels or secrets.
class WatchSelection {
  WatchSelection({
    required this.scope,
    required this.epoch,
    Map<String, Map<int, String>>? watches,
  }) : watches = watches ?? {};

  final String scope;
  final String epoch;
  final Map<String, Map<int, String>> watches;

  static WatchSelection? decode(String? value) {
    if (value == null) return null;
    try {
      final json = jsonDecode(value) as Map<String, dynamic>;
      if (json['version'] != 1) return null;
      return WatchSelection(
        scope: json['scope'] as String,
        epoch: json['epoch'] as String,
        watches: (json['watches'] as Map<String, dynamic>).map(
          (watch, entries) => MapEntry(
            watch,
            (entries as Map<String, dynamic>).map(
              (id, wireID) => MapEntry(int.parse(id), wireID as String),
            ),
          ),
        ),
      );
    } on Object {
      // Missing or unreadable consent is treated as a reset, never as selection.
      return null;
    }
  }

  String encode() => jsonEncode({
    'version': 1,
    'scope': scope,
    'epoch': epoch,
    'watches': watches.map(
      (watch, entries) => MapEntry(
        watch,
        entries.map((id, wireID) => MapEntry(id.toString(), wireID)),
      ),
    ),
  });
}
