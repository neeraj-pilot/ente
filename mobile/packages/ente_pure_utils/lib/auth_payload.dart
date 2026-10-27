import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';

void validateAuthToken(Uint8List token) {
  const prefix = 'auth-token';
  if (token.length != prefix.length + 32 ||
      String.fromCharCodes(token.take(prefix.length)) != prefix) {
    throw const FormatException(
      'Invalid login token. The server may need upgrading.',
    );
  }
}

String twoFactorRecoveryProof(
  String twoFactorType,
  String sessionID,
  String secret,
) {
  final message = utf8.encode(
    'ente-two-factor-recovery:v1\x00$twoFactorType\x00$sessionID',
  );
  return base64Encode(Hmac(sha256, utf8.encode(secret)).convert(message).bytes);
}
