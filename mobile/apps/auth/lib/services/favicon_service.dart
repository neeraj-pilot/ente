import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:ente_auth/services/favicon/image.dart';

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
          createHttpClient: () => HttpClient()
            ..maxConnectionsPerHost = 4
            ..findProxy = HttpClient.findProxyFromEnvironment,
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
    entry.result = _fetch(normalized, entry.cancel).then((icon) {
      entry.expires = _now().add(
        icon == null ? const Duration(minutes: 10) : const Duration(days: 1),
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
  }

  Future<Uint8List?> _fetch(List<String> domains, CancelToken cancel) async {
    final deadline = Timer(const Duration(seconds: 20), cancel.cancel);
    try {
      for (final domain in domains) {
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
            if (cancel.isCancelled) return null;
            final icon = await normalizeFavicon(bytes);
            if (icon != null) return cancel.isCancelled ? null : icon;
          } catch (_) {
            continue;
          }
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
