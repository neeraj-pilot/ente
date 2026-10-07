import 'dart:async';
import 'dart:typed_data';

import 'package:ente_auth/core/configuration.dart';
import 'package:ente_auth/services/favicon/cache.dart';
import 'package:ente_auth/services/favicon/image.dart';
import 'package:ente_auth/src/rust/api/icons.dart';
import 'package:ente_auth/src/rust/frb_generated.dart';
import 'package:ente_pure_utils/ente_pure_utils.dart';
import 'package:flutter/foundation.dart' show SynchronousFuture;

final faviconClient = FaviconClient();

final _labelPattern = RegExp(r'^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$');
final _numericHostPattern = RegExp(r'^[\d.]+$');

String? normalizeDomain(String input) {
  final domain = input.trim().toLowerCase().replaceFirst(RegExp(r'\.$'), '');
  if (domain.isEmpty) return null;
  if (domain.length > 253 ||
      !domain.contains('.') ||
      _numericHostPattern.hasMatch(domain) ||
      !domain.split('.').every(_labelPattern.hasMatch)) {
    throw const FormatException('Enter a domain name');
  }
  return domain;
}

class FaviconClient {
  late final Future<IconFetcher> _fetcher = _loadFetcher();
  final _diskCache = FaviconCache();
  final _cache = <String, CachedFavicon>{};
  final _pending = <String, _IconLookup>{};
  final _queue = SimpleTaskQueue(maxConcurrent: 6);
  bool _clearing = false;

  Future<IconFetcher> _loadFetcher() async {
    await EnteAuthRust.init();
    return IconFetcher();
  }

  Future<Uint8List?> fetch(List<String> domains) {
    if (_clearing) return Future.value();
    final normalized = <String>{};
    for (final domain in domains) {
      try {
        final value = normalizeDomain(domain);
        if (value != null) normalized.add(value);
      } on FormatException {
        continue;
      }
    }
    if (normalized.isEmpty) return Future.value();
    final config = Configuration.instance;
    final dataKey =
        config.hasOptedForOfflineMode() && !config.hasConfiguredAccount()
        ? config.getOfflineSecretKey()
        : config.getAuthSecretKey();
    final diskKey = dataKey == null
        ? null
        : FaviconCache.key(normalized.join(','), dataKey);
    final key = diskKey?.id ?? normalized.join(',');
    final cached = _cache.remove(key);
    if (cached != null && DateTime.now().isBefore(cached.expires)) {
      _cache[key] = cached;
      return SynchronousFuture(cached.bytes);
    }
    final pending = _pending[key];
    if (pending != null) return pending.result.future;
    final entry = _IconLookup(key, normalized, diskKey);
    _pending[key] = entry;
    unawaited(_queue.add(() => _run(entry)));
    return entry.result.future;
  }

  Future<void> clear() async {
    _clearing = true;
    for (final entry in _pending.values) {
      entry.cancel();
    }
    _pending.clear();
    _cache.clear();
    try {
      await _diskCache.clear();
    } finally {
      _clearing = false;
    }
  }

  Future<void> _run(_IconLookup entry) async {
    if (entry.cancelled) return;
    try {
      final diskKey = entry.diskKey;
      var cached = diskKey == null ? null : await _diskCache.read(diskKey);
      if (entry.cancelled) return;
      if (cached == null) {
        final icon = await _fetch(entry);
        if (entry.cancelled) return;
        cached = (
          bytes: icon,
          expires: DateTime.now().add(
            icon == null
                ? const Duration(minutes: 10)
                : const Duration(days: 30),
          ),
        );
        if (icon != null && diskKey != null) {
          await _diskCache.write(diskKey, icon, cached.expires);
        }
      }
      if (entry.cancelled) return;
      if (_cache.length >= 128) _cache.remove(_cache.keys.first);
      _cache[entry.key] = cached;
      entry.result.complete(cached.bytes);
    } catch (_) {
      return;
    } finally {
      if (!entry.result.isCompleted) entry.result.complete(null);
      if (identical(_pending[entry.key], entry)) _pending.remove(entry.key);
    }
  }

  Future<Uint8List?> _fetch(_IconLookup entry) async {
    Timer? deadline;
    var expired = false;
    try {
      final fetcher = await _fetcher;
      if (entry.cancelled) return null;
      final request = entry.request = fetcher.request();
      deadline = Timer(const Duration(seconds: 20), () {
        expired = true;
        request.cancel();
      });
      for (final domain in entry.domains) {
        if (entry.cancelled || expired) return null;
        try {
          final bytes = await request.fetch(url: 'https://$domain/');
          if (entry.cancelled || expired) return null;
          if (bytes == null) continue;
          final icon = await normalizeFavicon(bytes);
          if (icon != null) return entry.cancelled || expired ? null : icon;
        } catch (_) {
          continue;
        }
      }
      return null;
    } catch (_) {
      return null;
    } finally {
      deadline?.cancel();
      entry.request?.dispose();
      entry.request = null;
    }
  }
}

class _IconLookup {
  final String key;
  final Iterable<String> domains;
  final FaviconCacheKey? diskKey;
  final result = Completer<Uint8List?>();
  bool cancelled = false;
  IconRequest? request;

  _IconLookup(this.key, this.domains, this.diskKey);

  void cancel() {
    cancelled = true;
    request?.cancel();
    if (!result.isCompleted) result.complete(null);
  }
}
