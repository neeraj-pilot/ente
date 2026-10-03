import 'dart:convert';
import 'dart:isolate';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:ente_auth/services/favicon/image.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:xml/xml.dart';

Future<Uint8List?> normalizeSvgFavicon(Uint8List bytes) async {
  SvgStringLoader? loader;
  ui.Picture? source, scaled;
  ui.Image? image;
  try {
    final text = await Isolate.run(() => _svgSource(bytes));
    if (text == null) return null;
    loader = SvgStringLoader(text);
    final info = await vg.loadPicture(loader, null);
    source = info.picture;
    final size = info.size;
    if (!size.width.isFinite || !size.height.isFinite || size.isEmpty) {
      return null;
    }
    final scale = 128 / math.max(size.width, size.height);
    final recorder = ui.PictureRecorder();
    ui.Canvas(recorder)
      ..scale(scale)
      ..drawPicture(source);
    scaled = recorder.endRecording();
    image = await scaled.toImage(
      (size.width * scale).round().clamp(1, 128),
      (size.height * scale).round().clamp(1, 128),
    );
    final png = await image.toByteData(format: ui.ImageByteFormat.png);
    if (png == null) return null;
    final raster = png.buffer.asUint8List(png.offsetInBytes, png.lengthInBytes);
    return await Isolate.run(() => normalizeFavicon(raster));
  } catch (_) {
    return null;
  } finally {
    image?.dispose();
    scaled?.dispose();
    source?.dispose();
    if (loader != null) svg.cache.evict(loader.cacheKey(null));
  }
}

String? _svgSource(Uint8List bytes) {
  if (bytes.isEmpty || bytes.length > 256 * 1024) return null;
  final text = utf8.decode(bytes);
  final document = XmlDocument.parse(text);
  if (document.rootElement.name.local != 'svg' ||
      document.children.any((node) => node is XmlDoctype)) {
    return null;
  }
  var count = 0;
  for (final element in document.descendants.whereType<XmlElement>()) {
    if (++count > 2048 ||
        const {
          'image',
          'foreignObject',
          'script',
        }.contains(element.name.local)) {
      return null;
    }
    for (final attribute in element.attributes) {
      if (attribute.name.local == 'href' &&
          !attribute.value.trim().startsWith('#')) {
        return null;
      }
    }
  }
  return text;
}
