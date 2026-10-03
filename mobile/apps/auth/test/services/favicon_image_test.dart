import 'dart:typed_data';

import 'package:ente_auth/services/favicon/image.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;

void main() {
  test('normalizes indexed and 16-bit PNGs to plain RGBA bytes', () {
    final indexed = img.Image(
      width: 1,
      height: 1,
      numChannels: 4,
      withPalette: true,
    );
    indexed.palette!.setRgba(0, 20, 100, 200, 127);
    final highDepth = img.Image(
      width: 1,
      height: 1,
      format: img.Format.uint16,
      numChannels: 4,
    )..setPixelRgba(0, 0, 20 * 257, 100 * 257, 200 * 257, 127 * 257);
    for (final source in [indexed, highDepth]) {
      source.textData = {'Comment': 'discard me'};
      final icon = img.decodePng(normalizeFavicon(img.encodePng(source))!)!;
      expect(icon.format, img.Format.uint8);
      expect(icon.hasPalette, isFalse);
      expect(icon.getPixel(0, 0).toList(), [20, 100, 200, 127]);
      expect(icon.textData, isNot(contains('Comment')));
    }
  });

  test('selects the largest ICO PNG frame, including the 256 px size', () {
    final large = _solid(256, img.ColorRgba8(20, 100, 200, 127))
      ..textData = {'Comment': 'discard me'};
    final bytes = img.IcoEncoder().encodeImages([
      _solid(16, img.ColorRgba8(200, 20, 20, 255)),
      large,
      _solid(32, img.ColorRgba8(20, 200, 20, 255)),
    ]);
    final normalized = normalizeFavicon(bytes)!;
    final icon = img.decodePng(normalized)!;
    expect([icon.width, icon.height], [128, 128]);
    expect(icon.getPixel(0, 0).toList(), [20, 100, 200, 127]);
    expect(icon.textData, isNot(contains('Comment')));
    expect(normalized.length, lessThanOrEqualTo(64 * 1024));
  });

  test('selects and decodes the larger DIB frame in a mixed ICO', () {
    final bytes = _ico([
      (size: 16, bytes: img.encodePng(_solid(16, img.ColorRgb8(200, 0, 0)))),
      (size: 32, bytes: _dib(32)),
    ]);
    final icon = img.decodePng(normalizeFavicon(bytes)!)!;
    expect(icon.width, 32);
    expect(icon.getPixel(0, 0).toList(), [0, 128, 255, 255]);
  });

  test(
    'falls back when a larger ICO frame is damaged or over the size limit',
    () {
      final small = img.encodePng(_solid(16, img.ColorRgb8(0, 100, 200)));
      for (final badFrame in [
        Uint8List(40),
        Uint8List.sublistView(small, 0, 24),
        img.encodePng(img.Image(width: 2049, height: 1)),
      ]) {
        final icon = img.decodePng(
          normalizeFavicon(
            _ico([(size: 16, bytes: small), (size: 256, bytes: badFrame)]),
          )!,
        )!;
        expect(icon.width, 16);
        expect(icon.getPixel(0, 0).toList(), [0, 100, 200, 255]);
      }
    },
  );

  test('rejects an ICO with no usable frame', () {
    expect(normalizeFavicon(_ico([(size: 16, bytes: Uint8List(40))])), isNull);
  });

  test('uses a smaller visible ICO frame when the largest is transparent', () {
    final bytes = img.IcoEncoder().encodeImages([
      _solid(16, img.ColorRgba8(20, 100, 200, 255)),
      _solid(256, img.ColorRgba8(200, 100, 20, 0)),
    ]);
    final icon = img.decodePng(normalizeFavicon(bytes)!)!;
    expect(icon.width, 16);
    expect(icon.getPixel(0, 0).toList(), [20, 100, 200, 255]);
  });

  for (final foreground in [0, 255]) {
    test('backs a transparent $foreground mark without recoloring it', () {
      final image = _solid(8, img.ColorRgba8(200, 30, 90, 0));
      image.setPixelRgba(4, 4, foreground, foreground, foreground, 255);
      image.setPixelRgba(3, 4, foreground, foreground, foreground, 128);
      final icon = img.decodePng(normalizeFavicon(img.encodePng(image))!)!;
      final background = foreground == 0 ? 255 : 32;
      expect(icon.getPixel(0, 0).toList(), [
        background,
        background,
        background,
        255,
      ]);
      expect(icon.getPixel(4, 4).toList(), [
        foreground,
        foreground,
        foreground,
        255,
      ]);
      expect(icon.getPixel(3, 4).r, inInclusiveRange(120, 150));
      expect(icon.getPixel(3, 4).a, 255);
    });
  }

  test('preserves colorful transparency and opaque artwork', () {
    final source = _solid(8, img.ColorRgba8(20, 100, 200, 127));
    source.setPixelRgba(1, 1, 255, 0, 0, 200);
    source.setPixelRgba(2, 2, 0, 255, 0, 255);
    source.setPixelRgba(3, 3, 0, 0, 0, 0);
    final icon = img.decodePng(normalizeFavicon(img.encodePng(source))!)!;
    expect(icon.getPixel(0, 0).toList(), [20, 100, 200, 127]);
    expect(icon.getPixel(1, 1).toList(), [255, 0, 0, 200]);
    expect(icon.getPixel(2, 2).toList(), [0, 255, 0, 255]);
    expect(icon.getPixel(3, 3).a, 0);
    for (final shade in [0, 255]) {
      final opaque = img.decodePng(
        normalizeFavicon(
          img.encodePng(_solid(8, img.ColorRgba8(shade, shade, shade, 255))),
        )!,
      )!;
      expect(opaque.getPixel(0, 0).toList(), [shade, shade, shade, 255]);
    }
  });

  test('rejects fully transparent artwork regardless of invisible RGB', () {
    expect(
      normalizeFavicon(
        img.encodePng(_solid(8, img.ColorRgba8(20, 100, 200, 0))),
      ),
      isNull,
    );
  });
}

img.Image _solid(int size, img.Color color) => img.fill(
  img.Image(width: size, height: size, numChannels: 4),
  color: color,
);

Uint8List _ico(List<({int size, Uint8List bytes})> frames) {
  final directoryEnd = 6 + frames.length * 16;
  final bytes = Uint8List(
    directoryEnd +
        frames.fold<int>(0, (sum, frame) => sum + frame.bytes.length),
  );
  final header = ByteData.sublistView(bytes)
    ..setUint16(2, 1, Endian.little)
    ..setUint16(4, frames.length, Endian.little);
  var offset = directoryEnd;
  for (var i = 0; i < frames.length; i++) {
    final frame = frames[i];
    final entry = 6 + i * 16;
    bytes[entry] = bytes[entry + 1] = frame.size == 256 ? 0 : frame.size;
    header
      ..setUint16(entry + 4, 1, Endian.little)
      ..setUint16(entry + 6, 32, Endian.little)
      ..setUint32(entry + 8, frame.bytes.length, Endian.little)
      ..setUint32(entry + 12, offset, Endian.little);
    bytes.setRange(offset, offset + frame.bytes.length, frame.bytes);
    offset += frame.bytes.length;
  }
  return bytes;
}

Uint8List _dib(int size) {
  final bytes = Uint8List(40 + size * size * 4);
  ByteData.sublistView(bytes)
    ..setUint32(0, 40, Endian.little)
    ..setInt32(4, size, Endian.little)
    ..setInt32(8, size * 2, Endian.little)
    ..setUint16(12, 1, Endian.little)
    ..setUint16(14, 32, Endian.little);
  for (var i = 40; i < bytes.length; i += 4) {
    bytes.setRange(i, i + 4, [255, 128, 0, 255]);
  }
  return bytes;
}
