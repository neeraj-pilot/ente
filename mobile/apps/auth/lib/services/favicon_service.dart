import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:ente_auth/services/favicon/image.dart';
import 'package:ente_auth/services/favicon/svg.dart';
import 'package:pool/pool.dart';

final faviconClient = FaviconClient();

final _labelPattern = RegExp(r'^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$');
final _numericHostPattern = RegExp(r'^[\d.]+$');

List<String> parseDomains(String input) {
  if (input.trim().isEmpty) return const [];
  if (input.length > 2550) throw const FormatException('Too many domains');
  final result = <String>[];
  for (final part in input.split(',')) {
    final domain = part.trim().toLowerCase().replaceFirst(RegExp(r'\.$'), '');
    if (!_validDomain(domain)) {
      throw const FormatException('Enter domain names separated by commas');
    }
    if (!result.contains(domain)) result.add(domain);
  }
  if (result.length > 10) throw const FormatException('At most ten domains');
  return result;
}

bool _validDomain(String domain) =>
    domain.length <= 253 &&
    domain.contains('.') &&
    !_numericHostPattern.hasMatch(domain) &&
    domain.split('.').every(_labelPattern.hasMatch);

class FaviconClient {
  FaviconClient({HttpClientAdapter? adapter, DateTime Function()? now})
    : _now = now ?? DateTime.now {
    _dio.httpClientAdapter =
        adapter ??
        IOHttpClientAdapter(
          createHttpClient: () =>
              HttpClient()..findProxy = HttpClient.findProxyFromEnvironment,
        );
  }

  final _dio = Dio(
    BaseOptions(
      connectTimeout: const Duration(seconds: 3),
      receiveTimeout: const Duration(seconds: 10),
      responseType: ResponseType.stream,
      followRedirects: false,
      validateStatus: (status) => status == 200,
      headers: {'User-Agent': 'EnteAuth-Favicon'},
    ),
  );
  final DateTime Function() _now;
  final _pool = Pool(4);
  final _cache = <String, _CachedIcon>{};

  Future<Uint8List?> fetch(String domains) {
    final normalized = parseDomains(domains);
    if (normalized.isEmpty) return Future.value();
    final key = normalized.join(',');
    final cached = _cache.remove(key);
    if (cached != null &&
        (cached.expires == null || _now().isBefore(cached.expires!))) {
      _cache[key] = cached;
      return cached.result;
    }
    cached?.cancel.cancel();
    if (_cache.length >= 128) {
      _cache.remove(_cache.keys.first)?.cancel.cancel();
    }
    final entry = _CachedIcon();
    _cache[key] = entry;
    final result = normalized.length == 1
        ? _pool.withResource(
            () => entry.cancel.isCancelled
                ? Future<Uint8List?>.value()
                : _fetch(normalized.single, entry.cancel),
          )
        : _firstAvailable(normalized, entry.cancel);
    entry.result = result.then((icon) {
      entry.expires = _now().add(
        icon == null || normalized.length > 1
            ? const Duration(minutes: 10)
            : const Duration(days: 1),
      );
      return entry.cancel.isCancelled ? null : icon;
    });
    return entry.result;
  }

  void clear() {
    for (final entry in _cache.values) {
      entry.cancel.cancel();
    }
    _cache.clear();
  }

  void dispose() {
    clear();
    _dio.close(force: true);
    unawaited(_pool.close());
  }

  Future<Uint8List?> _firstAvailable(
    List<String> domains,
    CancelToken cancel,
  ) async {
    final deadline = Timer(const Duration(seconds: 20), cancel.cancel);
    try {
      for (final domain in domains) {
        if (cancel.isCancelled) return null;
        final icon = await Future.any([
          fetch(domain),
          cancel.whenCancel.then<Uint8List?>((_) => null),
        ]);
        if (icon != null) return icon;
      }
      return null;
    } finally {
      deadline.cancel();
    }
  }

  Future<Uint8List?> _fetch(String domain, CancelToken cancel) async {
    final deadline = Timer(const Duration(seconds: 20), cancel.cancel);
    try {
      for (final url in [
        Uri.https('icons.duckduckgo.com', '/ip3/$domain.ico'),
        Uri.https('news.kagi.com', '/api/favicon-proxy', {
          'domain': domain,
          'quality': 'best',
        }),
      ]) {
        if (cancel.isCancelled) return null;
        try {
          final bytes = await _download(url, cancel);
          final raster = await Isolate.run(() => normalizeFavicon(bytes));
          if (cancel.isCancelled) return null;
          final icon = raster ?? await normalizeSvgFavicon(bytes);
          if (icon != null) return cancel.isCancelled ? null : icon;
        } catch (_) {
          continue;
        }
      }
      return null;
    } finally {
      deadline.cancel();
    }
  }

  Future<Uint8List> _download(Uri url, CancelToken cancel) async {
    final response = await _dio.getUri<ResponseBody>(url, cancelToken: cancel);
    final body = response.data!;
    const limit = 2 * 1024 * 1024;
    if ((int.tryParse(response.headers.value('content-length') ?? '') ?? 0) >
        limit) {
      await body.stream.listen(null).cancel();
      throw const FormatException('Favicon response too large');
    }
    final data = BytesBuilder(copy: false);
    await for (final chunk in body.stream) {
      if (data.length + chunk.length > limit || cancel.isCancelled) {
        throw const FormatException('Favicon response too large or cancelled');
      }
      data.add(chunk);
    }
    return data.takeBytes();
  }
}

class _CachedIcon {
  final cancel = CancelToken();
  DateTime? expires;
  late final Future<Uint8List?> result;
}
