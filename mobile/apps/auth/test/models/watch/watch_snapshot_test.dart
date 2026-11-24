import 'dart:convert';
import 'dart:io';

import 'package:ente_auth/models/code.dart';
import 'package:ente_auth/models/code_display.dart';
import 'package:ente_auth/models/watch/snapshot.dart';
import 'package:ente_auth/services/preference_service.dart';
import 'package:ente_auth/utils/code_sort.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'encodes supported TOTP and Steam entries and excludes HOTP/trashed/error',
    () {
      final totp = Code.fromOTPAuthUrl(
        'otpauth://totp/GitHub:alice@example.com?secret=JBSWY3DPEHPK3PXP&algorithm=SHA256&digits=8&period=60',
      )..generatedID = 10;

      final steamAsTotp = Code.fromOTPAuthUrl(
        'otpauth://totp/alice?issuer=sTeAm&secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ',
      )..generatedID = 11;

      final hotp = Code.fromOTPAuthUrl(
        'otpauth://hotp/Legacy:alice?secret=JBSWY3DPEHPK3PXP&counter=3',
      )..generatedID = 12;

      final hotpWithSteamIssuer = Code.fromOTPAuthUrl(
        'otpauth://hotp/alice?issuer=Steam&secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&counter=5',
      )..generatedID = 13;

      final trashed = Code.fromOTPAuthUrl(
        'otpauth://totp/Deleted:alice?secret=JBSWY3DPEHPK3PXP',
        display: CodeDisplay(trashed: true),
      )..generatedID = 14;

      final errored = Code.withError('bad', 'otpauth://broken')
        ..generatedID = 15;

      final badSecret = Code.fromOTPAuthUrl(
        'otpauth://totp/Bad:alice?secret=INVALID!SECRET',
      )..generatedID = 16;

      expect(WatchSnapshot.supports(totp), true);
      expect(WatchSnapshot.supports(steamAsTotp), true);
      expect(WatchSnapshot.supports(hotp), false);
      expect(WatchSnapshot.supports(hotpWithSteamIssuer), false);
      expect(WatchSnapshot.supports(trashed), false);
      expect(WatchSnapshot.supports(errored), false);

      final snapshot = WatchSnapshot.fromCodes([
        totp,
        steamAsTotp,
        hotp,
        hotpWithSteamIssuer,
        trashed,
        errored,
        badSecret,
      ]);
      expect(snapshot.omittedCount, 1);

      final decoded =
          jsonDecode(utf8.decode(snapshot.data)) as Map<String, dynamic>;
      final entries = (decoded['entries'] as List).cast<Map<String, dynamic>>();
      expect(entries.length, 2);

      expect(entries[0]['id'], '10');
      expect(entries[0]['generator'], 'totp');
      expect(entries[0]['algorithm'], 'sha256');
      expect(entries[0]['digits'], 8);
      expect(entries[0]['period'], 60);

      expect(entries[1]['id'], '11');
      expect(entries[1]['generator'], 'steam');
      expect(entries[1]['algorithm'], 'sha1');
      expect(entries[1]['digits'], 5);
      expect(entries[1]['period'], 30);
      expect(decoded.containsKey('hideCodes'), false);
      expect(entries[0].containsKey('pinned'), false);
      expect(entries[0].containsKey('tags'), false);
    },
  );

  test(
    'WatchSnapshot encodes pinned, sorted tags, and hideCodes only when set',
    () {
      final code = Code.fromAccountAndSecret(
        Type.totp,
        'alice@example.com',
        'GitHub',
        'JBSWY3DPEHPK3PXP',
        CodeDisplay(pinned: true, tags: ['Work', 'Daily']),
        6,
      )..generatedID = 99;

      final snapshot = WatchSnapshot.fromCodes([code], hideCodes: true);
      final decoded =
          jsonDecode(utf8.decode(snapshot.data)) as Map<String, dynamic>;
      expect(decoded['hideCodes'], true);
      final entries = (decoded['entries'] as List).cast<Map<String, dynamic>>();
      expect(entries.length, 1);
      expect(entries[0]['pinned'], true);
      expect(entries[0]['tags'], ['Daily', 'Work']);
    },
  );

  test('WatchSnapshot encodes shared fixture secrets cleanly', () {
    final file = File('test/fixtures/watch_otp_vectors.json');
    final root = jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
    final otpVectors = (root['otp'] as List).cast<Map<String, dynamic>>();
    var id = 1;
    final codes = <Code>[];
    for (final vector in otpVectors) {
      final host = vector['generator'] == 'steam' ? 'steam' : 'totp';
      final uri =
          'otpauth://$host/${vector['name']}:alice'
          '?secret=${vector['secretBase32']}'
          '&algorithm=${(vector['algorithm'] as String).toUpperCase()}'
          '&digits=${vector['digits']}'
          '&period=${vector['period']}';
      final code = Code.fromOTPAuthUrl(uri)..generatedID = id++;
      codes.add(code);
    }

    final snapshot = WatchSnapshot.fromCodes(codes);
    expect(snapshot.omittedCount, 0);
    final decoded =
        jsonDecode(utf8.decode(snapshot.data)) as Map<String, dynamic>;
    final entries = (decoded['entries'] as List).cast<Map<String, dynamic>>();
    expect(entries.length, otpVectors.length);
    for (var i = 0; i < otpVectors.length; i++) {
      expect(entries[i]['secret'], otpVectors[i]['secretBase64']);
    }
  });

  test('sortAuthCodes respects pin order and CodeSortKey options', () {
    final zeta = Code.fromAccountAndSecret(
      Type.totp,
      'z@example.com',
      'Zeta',
      'JBSWY3DPEHPK3PXP',
      CodeDisplay(pinned: true, position: 2, tapCount: 1, lastUsedAt: 100),
      6,
    )..generatedID = 1;
    final alpha = Code.fromAccountAndSecret(
      Type.totp,
      'a@example.com',
      'Alpha',
      'JBSWY3DPEHPK3PXP',
      CodeDisplay(pinned: false, position: 0, tapCount: 9, lastUsedAt: 300),
      6,
    )..generatedID = 2;
    final beta = Code.fromAccountAndSecret(
      Type.totp,
      'b@example.com',
      'Beta',
      'JBSWY3DPEHPK3PXP',
      CodeDisplay(pinned: false, position: 1, tapCount: 5, lastUsedAt: 200),
      6,
    )..generatedID = 3;

    final byIssuer = [zeta, beta, alpha];
    sortAuthCodes(byIssuer, CodeSortKey.issuerName);
    expect(byIssuer.map((c) => c.issuer), ['Zeta', 'Alpha', 'Beta']);

    final byManual = [zeta, beta, alpha];
    sortAuthCodes(byManual, CodeSortKey.manual);
    expect(byManual.map((c) => c.issuer), ['Alpha', 'Beta', 'Zeta']);

    final byFreq = [beta, zeta, alpha];
    sortAuthCodes(byFreq, CodeSortKey.mostFrequentlyUsed);
    expect(byFreq.map((c) => c.issuer), ['Zeta', 'Alpha', 'Beta']);
  });
}
