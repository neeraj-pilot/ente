import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter_svg/flutter_svg.dart';

Future<Uint8List?> normalizeFavicon(Uint8List bytes) async {
  if (bytes.isEmpty || bytes.length > 2 * 1024 * 1024) return null;
  ui.Image? image;
  try {
    try {
      image = await _raster(bytes);
    } catch (_) {
      image = await _svg(bytes);
    }
    final pixels = (await image.toByteData(
      format: ui.ImageByteFormat.rawStraightRgba,
    ))!;
    var opacity = 0, dark = 0, light = 0;
    var transparent = false;
    for (var i = 0; i < pixels.lengthInBytes; i += 4) {
      final r = pixels.getUint8(i),
          g = pixels.getUint8(i + 1),
          b = pixels.getUint8(i + 2);
      final alpha = pixels.getUint8(i + 3);
      opacity += alpha;
      transparent |= alpha < 255;
      if (r < 64 && g < 64 && b < 64) dark += alpha;
      if (r > 224 && g > 224 && b > 224) light += alpha;
    }
    if (opacity == 0) return null;
    final background = !transparent
        ? null
        : dark >= opacity * 0.9
        ? const ui.Color(0xffffffff)
        : light >= opacity * 0.9
        ? const ui.Color(0xff202020)
        : null;
    if (background != null) {
      final recorder = ui.PictureRecorder();
      ui.Canvas(recorder)
        ..drawColor(background, ui.BlendMode.src)
        ..drawImage(image, ui.Offset.zero, ui.Paint());
      final picture = recorder.endRecording();
      try {
        final backed = await picture.toImage(image.width, image.height);
        image.dispose();
        image = backed;
      } finally {
        picture.dispose();
      }
    }
    final png = (await image.toByteData(format: ui.ImageByteFormat.png))!;
    return png.lengthInBytes <= 64 * 1024
        ? png.buffer.asUint8List(png.offsetInBytes, png.lengthInBytes)
        : null;
  } catch (_) {
    return null;
  } finally {
    image?.dispose();
  }
}

Future<ui.Image> _raster(Uint8List bytes) async {
  final buffer = await ui.ImmutableBuffer.fromUint8List(bytes);
  ui.ImageDescriptor? descriptor;
  ui.Codec? codec;
  try {
    descriptor = await ui.ImageDescriptor.encoded(buffer);
    final width = descriptor.width, height = descriptor.height;
    if (width <= 0 || height <= 0 || width > 2048 || height > 2048) {
      throw const FormatException('Favicon dimensions too large');
    }
    final scale = math.min(1.0, 128 / math.max(width, height));
    codec = await descriptor.instantiateCodec(
      targetWidth: (width * scale).floor().clamp(1, 128),
      targetHeight: (height * scale).floor().clamp(1, 128),
    );
    return (await codec.getNextFrame()).image;
  } finally {
    codec?.dispose();
    descriptor?.dispose();
    buffer.dispose();
  }
}

Future<ui.Image> _svg(Uint8List bytes) async {
  if (bytes.length > 256 * 1024) throw const FormatException('SVG too large');
  final loader = SvgBytesLoader(bytes);
  ui.Picture? original, scaled;
  try {
    final info = await vg.loadPicture(loader, null);
    original = info.picture;
    final size = info.size;
    if (!size.width.isFinite || !size.height.isFinite || size.isEmpty) {
      throw const FormatException('Invalid SVG dimensions');
    }
    final scale = 128 / math.max(size.width, size.height);
    final recorder = ui.PictureRecorder();
    ui.Canvas(recorder)
      ..scale(scale)
      ..drawPicture(original);
    scaled = recorder.endRecording();
    return await scaled.toImage(
      (size.width * scale).round().clamp(1, 128),
      (size.height * scale).round().clamp(1, 128),
    );
  } finally {
    scaled?.dispose();
    original?.dispose();
    svg.cache.evict(loader.cacheKey(null));
  }
}
