import 'package:ente_pure_utils/ente_pure_utils.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('isValidEmail', () {
    test('accepts practical internet email addresses', () {
      const validEmails = [
        'user@example.com',
        'USER@EXAMPLE.COM',
        'first.last+tag@sub-domain.example',
        "o'hara@example.com",
        'user_name@example.com',
        'a@b.co',
        'user@xn--bcher-kva.de',
        'user@example.xn--p1ai',
      ];

      for (final email in validEmails) {
        expect(isValidEmail(email), isTrue, reason: email);
      }
    });

    test('rejects missing or ambiguous address parts', () {
      const invalidEmails = [
        '',
        'user',
        '@example.com',
        'user@',
        'user@@example.com',
      ];

      for (final email in invalidEmails) {
        expect(isValidEmail(email), isFalse, reason: email);
      }
    });

    test('rejects whitespace and unsupported local-part forms', () {
      const invalidEmails = [
        ' user@example.com',
        'user@example.com ',
        'user name@example.com',
        '.user@example.com',
        'user.@example.com',
        'user..name@example.com',
        r'"user name"@example.com',
        r'user\name@example.com',
        'user:name@example.com',
        'user!name@example.com',
        'user#name@example.com',
        r'user$name@example.com',
        'user/name@example.com',
        'user=name@example.com',
        'user~name@example.com',
      ];

      for (final email in invalidEmails) {
        expect(isValidEmail(email), isFalse, reason: email);
      }
    });

    test('rejects non-public or malformed domains', () {
      const invalidEmails = [
        'user@localhost',
        'user@example.c',
        'user@example.123',
        'user@example.a1',
        'user@example.xn--',
        'user@example.xn---invalid',
        'user@-example.com',
        'user@example-.com',
        'user@example..com',
        'user@example_com',
        'user@example.com.',
        'user@[127.0.0.1]',
      ];

      for (final email in invalidEmails) {
        expect(isValidEmail(email), isFalse, reason: email);
      }
    });

    test('enforces email and DNS length limits', () {
      final localPartAtLimit = 'a' * 64;
      final localPartOverLimit = 'a' * 65;
      final domainLabelAtLimit = 'b' * 63;
      final domainLabelOverLimit = 'b' * 64;
      final emailAtLimit = '${'a' * 64}@${'b' * 63}.${'c' * 63}.${'d' * 61}';
      final emailOverLimit = '${'a' * 64}@${'b' * 63}.${'c' * 63}.${'d' * 62}';

      expect(isValidEmail('$localPartAtLimit@example.com'), isTrue);
      expect(isValidEmail('$localPartOverLimit@example.com'), isFalse);
      expect(isValidEmail('user@$domainLabelAtLimit.com'), isTrue);
      expect(isValidEmail('user@$domainLabelOverLimit.com'), isFalse);
      expect(emailAtLimit.length, 254);
      expect(isValidEmail(emailAtLimit), isTrue);
      expect(emailOverLimit.length, 255);
      expect(isValidEmail(emailOverLimit), isFalse);
    });

    test('rejects Unicode until EAI and IDNA canonicalization are owned', () {
      expect(isValidEmail('josé@example.com'), isFalse);
      expect(isValidEmail('user@例え.jp'), isFalse);
      expect(isValidEmail('😀@example.com'), isFalse);
      expect(isValidEmail('user@exam\u200Bple.com'), isFalse);
    });
  });
}
