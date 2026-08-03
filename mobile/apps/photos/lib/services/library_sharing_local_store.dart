import 'dart:convert';

import 'package:logging/logging.dart';
import 'package:photos/models/collection/collection.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _keyPrefix = 'library_sharing_local_';

class LibrarySharingLocalConfig {
  const LibrarySharingLocalConfig({
    required this.recipientUserID,
    required this.enabled,
    required this.defaultRole,
    this.addedAutomatically = const {},
    this.unsharedBefore = const {},
    this.hidden = const {},
  });

  final int recipientUserID;
  final bool enabled;
  final CollectionParticipantRole defaultRole;
  final Set<int> addedAutomatically;
  final Set<int> unsharedBefore;
  final Set<int> hidden;

  LibrarySharingLocalConfig copyWith({
    bool? enabled,
    CollectionParticipantRole? defaultRole,
    Set<int>? addedAutomatically,
    Set<int>? unsharedBefore,
    Set<int>? hidden,
  }) => LibrarySharingLocalConfig(
    recipientUserID: recipientUserID,
    enabled: enabled ?? this.enabled,
    defaultRole: defaultRole ?? this.defaultRole,
    addedAutomatically: addedAutomatically ?? this.addedAutomatically,
    unsharedBefore: unsharedBefore ?? this.unsharedBefore,
    hidden: hidden ?? this.hidden,
  );

  Map<String, dynamic> toJson() => {
    'recipientUserID': recipientUserID,
    'enabled': enabled,
    'defaultRole': defaultRole.name,
    'addedAutomatically': addedAutomatically.toList()..sort(),
    'unsharedBefore': unsharedBefore.toList()..sort(),
    'hidden': hidden.toList()..sort(),
  };

  factory LibrarySharingLocalConfig.fromJson(Map<String, dynamic> json) {
    final role = CollectionParticipantRoleExtn.fromString(
      json['defaultRole'] as String?,
    );
    return LibrarySharingLocalConfig(
      recipientUserID: json['recipientUserID'] as int,
      enabled: json['enabled'] as bool? ?? false,
      defaultRole: switch (role) {
        CollectionParticipantRole.viewer ||
        CollectionParticipantRole.collaborator ||
        CollectionParticipantRole.admin => role,
        _ => CollectionParticipantRole.viewer,
      },
      addedAutomatically: _intSet(json['addedAutomatically']),
      unsharedBefore: _intSet(json['unsharedBefore']),
      hidden: _intSet(json['hidden']),
    );
  }
}

class LibrarySharingLocalStore {
  LibrarySharingLocalStore([SharedPreferences? preferences])
    : _preferences = preferences;

  final SharedPreferences? _preferences;
  final _logger = Logger('LibrarySharingLocalStore');

  Future<LibrarySharingLocalConfig?> read(
    int ownerUserID,
    int recipientUserID,
  ) async {
    final preferences = _preferences ?? await SharedPreferences.getInstance();
    final key = _key(ownerUserID, recipientUserID);
    final encoded = preferences.getString(key);
    if (encoded == null) {
      return null;
    }
    try {
      final config = LibrarySharingLocalConfig.fromJson(
        Map<String, dynamic>.from(jsonDecode(encoded) as Map),
      );
      if (config.recipientUserID != recipientUserID) {
        throw const FormatException('Recipient does not match storage key');
      }
      return config;
    } catch (error, stackTrace) {
      _logger.warning(
        'Discarding invalid local library sharing state',
        error,
        stackTrace,
      );
      await preferences.remove(key);
      return null;
    }
  }

  Future<List<LibrarySharingLocalConfig>> readAll(int ownerUserID) async {
    final preferences = _preferences ?? await SharedPreferences.getInstance();
    final prefix = '$_keyPrefix${ownerUserID}_';
    final configs = <LibrarySharingLocalConfig>[];
    for (final key in preferences.getKeys().where(
      (key) => key.startsWith(prefix),
    )) {
      final recipientUserID = int.tryParse(key.substring(prefix.length));
      if (recipientUserID == null) {
        continue;
      }
      final config = await read(ownerUserID, recipientUserID);
      if (config != null) {
        configs.add(config);
      }
    }
    return configs;
  }

  Future<void> write(int ownerUserID, LibrarySharingLocalConfig config) async {
    final preferences = _preferences ?? await SharedPreferences.getInstance();
    final key = _key(ownerUserID, config.recipientUserID);
    final encoded = jsonEncode(config.toJson());
    if (preferences.getString(key) == encoded) {
      return;
    }
    final didWrite = await preferences.setString(key, encoded);
    if (!didWrite) {
      throw StateError('Could not persist local library sharing state');
    }
  }

  String _key(int ownerUserID, int recipientUserID) =>
      '$_keyPrefix${ownerUserID}_$recipientUserID';
}

Set<int> _intSet(dynamic value) =>
    value is List ? value.whereType<int>().toSet() : <int>{};
