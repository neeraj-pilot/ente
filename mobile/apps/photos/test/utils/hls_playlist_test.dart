import 'package:flutter_test/flutter_test.dart';
import 'package:photos/utils/hls_playlist.dart';

const playlist = '''#EXTM3U
#EXT-X-VERSION:4
#EXT-X-TARGETDURATION:8
#EXT-X-MEDIA-SEQUENCE:0
#EXT-X-KEY:METHOD=AES-128,URI="data:text/plain;base64,XjvG7qeRrsOpPUbJPh2Ikg==",IV=0x00000000000000000000000000000000
#EXTINF:8.333333,
#EXT-X-BYTERANGE:3046928@0
output.ts
#EXTINF:2.200000,
#EXT-X-BYTERANGE:834736@3046928
output.ts
#EXT-X-ENDLIST
''';

void main() {
  group('reconstructHlsPlaylist', () {
    test('reconstructs a generated single-file playlist', () {
      const segmentUrl = 'https://museum.example/video?token=secret';

      expect(
        reconstructHlsPlaylist(playlist, segmentUrl),
        playlist.replaceAll('output.ts', segmentUrl),
      );
    });

    final invalidPlaylists = {
      'remote segment': playlist.replaceFirst('output.ts', 'https://attacker'),
      'remote key': playlist.replaceFirst(
        'data:text/plain;base64,XjvG7qeRrsOpPUbJPh2Ikg==',
        'https://attacker/key',
      ),
      'map': playlist.replaceFirst(
        '#EXTINF:8.333333,',
        '#EXT-X-MAP:URI="https://attacker/map"\n#EXTINF:8.333333,',
      ),
    };

    for (final entry in invalidPlaylists.entries) {
      test('rejects a playlist with an unexpected ${entry.key}', () {
        expect(
          () => reconstructHlsPlaylist(entry.value, 'https://museum/video'),
          throwsFormatException,
        );
      });
    }
  });
}
