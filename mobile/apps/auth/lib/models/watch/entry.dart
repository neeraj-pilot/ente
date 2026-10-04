import 'dart:convert';
import 'dart:typed_data';

import 'package:base32/base32.dart';
import 'package:ente_auth/models/code.dart';
import 'package:ente_auth/utils/totp_util.dart';

enum WatchGenerator { totp, steam }

class WatchEntry {
  const WatchEntry({
    required this.id,
    required this.issuer,
    required this.account,
    required this.secret,
    required this.generator,
    required this.algorithm,
    required this.digits,
    required this.period,
  });

  final String id;
  final String issuer;
  final String account;
  final Uint8List secret;
  final WatchGenerator generator;
  final Algorithm algorithm;
  final int digits;
  final int period;

  // Match Auth's existing Steam issuer alias as well as the explicit Steam type.
  static bool isSteam(Code code) =>
      code.type == Type.steam || code.issuer.toLowerCase() == 'steam';

  static bool supports(Code code) =>
      !code.hasError &&
      !code.isTrashed &&
      (isSteam(code) || code.type == Type.totp);

  factory WatchEntry.fromCode(Code code, {required String id}) {
    if (!supports(code) || id.isEmpty) {
      throw const FormatException('This account cannot be sent to a watch.');
    }
    final steam = isSteam(code);
    final digits = steam ? 5 : code.digits;
    final period = steam ? 30 : code.period;
    if (digits < 1 || digits > 10 || period <= 0 || period > 0x7fffffff) {
      throw const FormatException('Unsupported OTP settings.');
    }
    final secret = base32.decode(getSanitizedSecret(code.secret));
    if (secret.isEmpty) {
      throw const FormatException('Missing OTP secret.');
    }
    return WatchEntry(
      id: id,
      issuer: code.issuer,
      account: code.account,
      secret: secret,
      generator: steam ? WatchGenerator.steam : WatchGenerator.totp,
      algorithm: steam ? Algorithm.sha1 : code.algorithm,
      digits: digits,
      period: period,
    );
  }

  Map<String, Object> toJson() => {
    'id': id,
    'issuer': issuer,
    'account': account,
    'secret': base64Encode(secret),
    'generator': generator.name,
    'algorithm': algorithm.name,
    'digits': digits,
    'period': period,
  };
}

class WatchSnapshot {
  const WatchSnapshot(this.entries, this.omittedIDs);

  final List<WatchEntry> entries;
  final Set<int> omittedIDs;

  // A bad selected entry must not prevent a different entry from being removed.
  factory WatchSnapshot.fromCodes(
    List<Code> codes,
    Map<int, String> selection,
  ) {
    final entries = <WatchEntry>[];
    final omitted = <int>{};
    for (final code in codes) {
      final id = selection[code.generatedID];
      if (id == null || code.isTrashed) continue;
      try {
        entries.add(WatchEntry.fromCode(code, id: id));
      } on FormatException {
        omitted.add(code.generatedID!);
      }
    }
    entries.sort((a, b) {
      final issuer = a.issuer.toLowerCase().compareTo(b.issuer.toLowerCase());
      if (issuer != 0) return issuer;
      final account = a.account.toLowerCase().compareTo(
        b.account.toLowerCase(),
      );
      return account != 0 ? account : a.id.compareTo(b.id);
    });
    return WatchSnapshot(entries, omitted);
  }

  Uint8List encode() => Uint8List.fromList(
    utf8.encode(
      jsonEncode({'entries': entries.map((e) => e.toJson()).toList()}),
    ),
  );
}
