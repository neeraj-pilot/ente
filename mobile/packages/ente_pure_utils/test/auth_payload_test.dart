import 'dart:convert';
import 'dart:typed_data';

import 'package:ente_pure_utils/auth_payload.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('requires the full tagged login token', () {
    final token = Uint8List.fromList([
      ...ascii.encode('auth-token'),
      ...List<int>.generate(32, (index) => index),
    ]);
    final original = Uint8List.fromList(token);
    validateAuthToken(token);
    expect(token, original);
    for (final invalid in [
      Uint8List(32),
      Uint8List.fromList(ascii.encode('delete-jwtheader.payload.signature')),
      Uint8List.fromList([...ascii.encode('wrong-type'), ...token.sublist(10)]),
      token.sublist(0, 41),
      Uint8List.fromList([...token, 0]),
    ]) {
      expect(() => validateAuthToken(invalid), throwsFormatException);
    }
  });

  test('proofs bind factor and session', () {
    const totpSecret = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';
    const totpProof = 'GLPadofZrER+5+7OLkqiNxlmyS6XzaKGdsMeHg1lnvU=';
    for (final (factor, secret, expected) in [
      ('totp', totpSecret, totpProof),
      (
        'passkey',
        'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=',
        'PxcSH0NrKUX3u330jU6TtYp/I/mT4087vajBXIdPh0I=',
      ),
    ]) {
      expect(
        twoFactorRecoveryProof(factor, 'recovery-session', secret),
        expected,
      );
      expect(
        twoFactorRecoveryProof(factor, 'other-session', secret),
        isNot(expected),
      );
      expect(
        twoFactorRecoveryProof(
          factor == 'totp' ? 'passkey' : 'totp',
          'recovery-session',
          secret,
        ),
        isNot(expected),
      );
    }
  });
}
