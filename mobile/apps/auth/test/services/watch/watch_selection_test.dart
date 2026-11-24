import 'package:ente_auth/models/code.dart';
import 'package:ente_auth/models/code_display.dart';
import 'package:ente_auth/services/watch/selection.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('WatchSharing includes matching codes for all and tags modes', () {
    final workCode = Code.fromAccountAndSecret(
      Type.totp,
      'alice@work.com',
      'GitHub',
      'JBSWY3DPEHPK3PXP',
      CodeDisplay(tags: ['Work', 'Daily']),
      6,
    );
    final personalCode = Code.fromAccountAndSecret(
      Type.totp,
      'alice@home.com',
      'Proton',
      'JBSWY3DPEHPK3PXP',
      CodeDisplay(tags: ['Personal']),
      6,
    );

    const allSharing = WatchSharing(mode: WatchSharingMode.all);
    expect(allSharing.includes(workCode), true);
    expect(allSharing.includes(personalCode), true);

    const tagSharing = WatchSharing(
      mode: WatchSharingMode.tags,
      tags: {'Work'},
    );
    expect(tagSharing.includes(workCode), true);
    expect(tagSharing.includes(personalCode), false);

    final pinnedCode = Code.fromAccountAndSecret(
      Type.totp,
      'pin@example.com',
      'PinnedApp',
      'JBSWY3DPEHPK3PXP',
      CodeDisplay(pinned: true),
      6,
    );
    const pinnedSharing = WatchSharing(mode: WatchSharingMode.pinned);
    expect(pinnedSharing.includes(pinnedCode), true);
    expect(pinnedSharing.includes(workCode), false);
    expect(pinnedSharing.includes(personalCode), false);
  });

  test('WatchSelection round-trips JSON and rejects invalid payloads', () {
    final selection = WatchSelection(
      scope: 'account:42',
      epoch: 'epoch-1',
      watches: {
        'enroll-1': const WatchSharing(
          mode: WatchSharingMode.tags,
          tags: {'Work', 'Bank'},
        ),
        'enroll-2': const WatchSharing(mode: WatchSharingMode.pinned),
      },
    );

    final decoded = WatchSelection.decode(selection.encode());
    expect(decoded, isNotNull);
    expect(decoded!.scope, 'account:42');
    expect(decoded.epoch, 'epoch-1');
    expect(decoded.watches['enroll-1']?.mode, WatchSharingMode.tags);
    expect(decoded.watches['enroll-1']?.tags, {'Work', 'Bank'});
    expect(decoded.watches['enroll-2']?.mode, WatchSharingMode.pinned);
    expect(decoded.watches['enroll-2']?.tags, isEmpty);

    expect(WatchSelection.decode(null), isNull);
    expect(WatchSelection.decode('not json'), isNull);
    expect(WatchSelection.decode('{"version":1}'), isNull);
  });
}
