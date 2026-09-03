final _keyTag = RegExp(
  r'^#EXT-X-KEY:METHOD=AES-128,URI="data:text/plain;base64,[A-Za-z0-9+/]{22}==",IV=0x[0-9a-fA-F]{32}$',
);
final _durationTag = RegExp(r'^#EXTINF:\d+(?:\.\d+)?,$');
final _byteRangeTag = RegExp(r'^#EXT-X-BYTERANGE:\d+@\d+$');

String reconstructHlsPlaylist(String template, String segmentUrl) {
  if (template.contains('\r')) {
    throw const FormatException('Invalid HLS playlist');
  }

  final lines = template
      .split('\n')
      .where((line) => !line.startsWith('#') || line.startsWith('#EXT'))
      .toList();
  if (lines.isNotEmpty && lines.last.isEmpty) lines.removeLast();

  if (lines.length < 9 ||
      lines[0] != '#EXTM3U' ||
      lines[1] != '#EXT-X-VERSION:4' ||
      !RegExp(r'^#EXT-X-TARGETDURATION:\d+$').hasMatch(lines[2]) ||
      lines[3] != '#EXT-X-MEDIA-SEQUENCE:0' ||
      !_keyTag.hasMatch(lines[4]) ||
      lines.last != '#EXT-X-ENDLIST' ||
      (lines.length - 6) % 3 != 0) {
    throw const FormatException('Invalid HLS playlist');
  }

  final result = lines.sublist(0, 5);
  for (var i = 5; i < lines.length - 1; i += 3) {
    if (!_durationTag.hasMatch(lines[i]) ||
        !_byteRangeTag.hasMatch(lines[i + 1]) ||
        lines[i + 2] != 'output.ts') {
      throw const FormatException('Invalid HLS playlist');
    }
    result.addAll([lines[i], lines[i + 1], segmentUrl]);
  }

  return '${[...result, '#EXT-X-ENDLIST'].join('\n')}\n';
}
