import 'dart:typed_data';

import 'package:image/image.dart' as img;

Uint8List? normalizeFavicon(Uint8List bytes) {
  if (bytes.length < 8 || bytes.length > 2 * 1024 * 1024) return null;
  try {
    final decoder = img.findDecoderForData(bytes);
    if (decoder is! img.PngDecoder &&
        decoder is! img.JpegDecoder &&
        decoder is! img.WebPDecoder &&
        decoder is! img.IcoDecoder) {
      return null;
    }
    var image = decoder is img.IcoDecoder
        ? _decodeIco(bytes, decoder)
        : _decodeFrame(bytes, decoder!);
    if (image == null || !_validSize(image.width, image.height)) return null;
    image = img.bakeOrientation(image);
    for (final size in [128, 96]) {
      final longest = image.width > image.height ? image.width : image.height;
      final resized = longest <= size
          ? image
          : img.copyResize(
              image,
              width: (image.width * size ~/ longest).clamp(1, size),
              height: (image.height * size ~/ longest).clamp(1, size),
              interpolation: img.Interpolation.average,
            );
      final rgba = resized
          .convert(format: img.Format.uint8, numChannels: 4)
          .getBytes();
      final clean = img.Image.fromBytes(
        width: resized.width,
        height: resized.height,
        bytes: rgba.buffer,
        bytesOffset: rgba.offsetInBytes,
        numChannels: 4,
      );
      final visible = _withContrastBackground(clean);
      if (visible == null) return null;
      final png = img.encodePng(visible);
      if (png.length <= 64 * 1024) return png;
    }
  } catch (_) {
    return null;
  }
  return null;
}

bool _validSize(int width, int height) =>
    width > 0 && height > 0 && width <= 2048 && height <= 2048;

img.Image? _decodeFrame(Uint8List bytes, img.Decoder decoder) {
  final info = decoder.startDecode(bytes);
  if (info == null || !_validSize(info.width, info.height)) return null;
  return decoder.decodeFrame(0);
}

img.Image? _decodeIco(Uint8List bytes, img.IcoDecoder decoder) {
  final header = ByteData.sublistView(bytes);
  final count = header.getUint16(4, Endian.little);
  final directoryEnd = 6 + count * 16;
  if (count == 0 || count > 64 || directoryEnd > bytes.length) return null;
  final frames = List.generate(count, (index) {
    final entry = 6 + index * 16;
    final width = bytes[entry] == 0 ? 256 : bytes[entry];
    final height = bytes[entry + 1] == 0 ? 256 : bytes[entry + 1];
    return (index: index, size: width * height);
  })..sort((a, b) => b.size.compareTo(a.size));
  if (decoder.startDecode(bytes) == null) return null;
  for (final candidate in frames) {
    try {
      final entry = 6 + candidate.index * 16;
      final length = header.getUint32(entry + 8, Endian.little);
      final offset = header.getUint32(entry + 12, Endian.little);
      if (offset < directoryEnd ||
          length < 16 ||
          offset + length > bytes.length) {
        continue;
      }
      final frame = Uint8List.sublistView(bytes, offset, offset + length);
      final png = img.PngDecoder();
      img.Image? image;
      if (png.isValidFile(frame)) {
        image = _decodeFrame(frame, png);
      } else {
        final dib = ByteData.sublistView(frame);
        if (frame.length < 40 ||
            dib.getUint32(0, Endian.little) != 40 ||
            !_validSize(
              dib.getInt32(4, Endian.little),
              dib.getInt32(8, Endian.little).abs() ~/ 2,
            )) {
          continue;
        }
        image = decoder.decodeFrame(candidate.index);
      }
      if (image != null &&
          _validSize(image.width, image.height) &&
          image.any((pixel) => pixel.aNormalized > 0)) {
        return image;
      }
    } catch (_) {
      continue;
    }
  }
  return null;
}

img.Image? _withContrastBackground(img.Image image) {
  var transparent = false;
  var opacity = 0.0, dark = 0.0, light = 0.0;
  for (final pixel in image) {
    final alpha = pixel.aNormalized;
    transparent |= alpha < 1;
    opacity += alpha;
    if (pixel.r < 64 && pixel.g < 64 && pixel.b < 64) dark += alpha;
    if (pixel.r > 224 && pixel.g > 224 && pixel.b > 224) light += alpha;
  }
  if (opacity == 0) return null;
  if (!transparent) return image;
  final shade = dark >= opacity * 0.9
      ? 255
      : light >= opacity * 0.9
      ? 32
      : null;
  if (shade == null) return image;
  final background = img.Image(
    width: image.width,
    height: image.height,
    numChannels: 4,
  );
  img.fill(background, color: img.ColorRgba8(shade, shade, shade, 255));
  return img.compositeImage(background, image);
}
