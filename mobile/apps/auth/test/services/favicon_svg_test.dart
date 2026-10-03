import 'dart:convert';
import 'dart:typed_data';

import 'package:ente_auth/services/favicon/svg.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'renders SVG paths and local gradients while preserving aspect ratio',
    () async {
      const source =
          '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 32">'
          '<defs><linearGradient id="g"><stop stop-color="#ff0000"/>'
          '<stop offset="1" stop-color="#0000ff"/></linearGradient></defs>'
          '<path fill="url(#g)" d="M0 0H64V32H0Z"/></svg>';
      final previousCacheSize = svg.cache.count;
      final png = await normalizeSvgFavicon(utf8.encode(source));
      final image = img.decodePng(png!)!;
      expect([image.width, image.height], [128, 64]);
      expect(image.getPixel(0, 32).r, greaterThan(240));
      expect(image.getPixel(127, 32).b, greaterThan(240));
      expect(svg.cache.count, previousCacheSize);
    },
  );

  for (final color in ['black', 'white']) {
    test('normalizes transparent $color SVGs for both themes', () async {
      final png = await normalizeSvgFavicon(
        utf8.encode(
          '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">'
          '<circle cx="16" cy="16" r="8" fill="$color"/></svg>',
        ),
      );
      final image = img.decodePng(png!)!;
      expect(
        image.getPixel(0, 0).toList(),
        color == 'black' ? [255, 255, 255, 255] : [32, 32, 32, 255],
      );
      expect(image.getPixel(64, 64).r, color == 'black' ? 0 : 255);
    });
  }

  test('rejects malformed, oversized, unsupported and invisible SVGs', () async {
    for (final bytes in [
      Uint8List(256 * 1024 + 1),
      Uint8List.fromList([0xff, 0xff]),
      utf8.encode('<html/>'),
      utf8.encode('<svg'),
      utf8.encode('<!DOCTYPE svg><svg/>'),
      utf8.encode('<svg><image href="https://example.com/icon.png"/></svg>'),
      utf8.encode('<svg><image href="data:image/png;base64,AAAA"/></svg>'),
      utf8.encode('<svg><use href="https://example.com/icon.svg#icon"/></svg>'),
      utf8.encode('<svg><foreignObject/></svg>'),
      utf8.encode(
        '<svg viewBox="0 0 32 32"><rect width="32" height="32" fill="none"/></svg>',
      ),
      utf8.encode('<svg>${List.filled(2048, '<g/>').join()}</svg>'),
    ]) {
      expect(await normalizeSvgFavicon(bytes), isNull);
    }
  });
}
