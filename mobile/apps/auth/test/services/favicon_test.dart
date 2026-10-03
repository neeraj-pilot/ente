import 'dart:async';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:ente_auth/services/favicon/image.dart';
import 'package:ente_auth/services/favicon_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('normalizes comma-separated domains without changing their order', () {
    expect(parseDomains(' Example.com., login.example.org, EXAMPLE.com '), [
      'example.com',
      'login.example.org',
    ]);
    expect(parseDomains('xn--bcher-kva.example'), ['xn--bcher-kva.example']);
    expect(parseDomains('  '), isEmpty);
    for (final input in [
      'https://example.com',
      'example.com/path',
      'a@example.com',
      '127.0.0.1',
      '[::1]',
      'localhost',
      'example.com:443',
      'example.com,',
      '-bad.example',
      'bad-.example',
      'bad domain.example',
      'a..example',
      List.generate(11, (i) => 'example$i.com').join(','),
    ]) {
      expect(() => parseDomains(input), throwsFormatException, reason: input);
    }
  });

  test(
    'returns bounded PNGs for PNG, JPEG and ICO without source metadata',
    () {
      final source = img.Image(width: 240, height: 160, numChannels: 4)
        ..textData = {'Comment': 'not retained'};
      img.fill(source, color: img.ColorRgba8(20, 100, 200, 127));
      for (final bytes in [
        img.encodePng(source),
        img.encodeJpg(source),
        img.encodeIco(source),
      ]) {
        final result = normalizeFavicon(bytes)!;
        final decoded = img.decodePng(result)!;
        expect(decoded.width, 128);
        expect(decoded.height, 85);
        expect(result.length, lessThanOrEqualTo(64 * 1024));
        expect(decoded.textData, isNot(contains('Comment')));
      }
      final png = img.decodePng(normalizeFavicon(img.encodePng(source))!)!;
      expect(png.getPixel(0, 0).a, 127);
    },
  );

  test('rejects invalid and oversized images before displaying them', () {
    expect(normalizeFavicon(Uint8List.fromList([1, 2, 3])), isNull);
    expect(normalizeFavicon(Uint8List(2 * 1024 * 1024 + 1)), isNull);
    expect(
      normalizeFavicon(img.encodePng(img.Image(width: 2049, height: 1))),
      isNull,
    );
    final narrow = normalizeFavicon(img.encodePng(_source(1, 512)))!;
    expect(img.decodePng(narrow)!.width, 1);
  });

  test(
    'uses DuckDuckGo, normalizes and caches without contacting websites',
    () async {
      final adapter = _Adapter(
        (_) => ResponseBody.fromBytes(img.encodeIco(_source(32, 32)), 200),
      );
      final client = FaviconClient(adapter: adapter);
      addTearDown(client.dispose);
      final first = client.fetch('EXAMPLE.com');
      expect(identical(first, client.fetch('example.com')), isTrue);
      expect(img.decodePng((await first)!)!.width, 32);
      expect(await client.fetch('example.com'), isNotNull);
      expect(adapter.requests.map((r) => r.uri.toString()), [
        'https://icons.duckduckgo.com/ip3/example.com.ico',
      ]);
      expect(
        adapter.requests.single.headers.keys.map((key) => key.toLowerCase()),
        isNot(anyOf(contains('authorization'), contains('cookie'))),
      );
      client.clear();
      await client.fetch('example.com');
      expect(adapter.requests, hasLength(2));
    },
  );

  test('falls back to Kagi best and caches an SVG as a bounded PNG', () async {
    final adapter = _Adapter(
      (request) => request.uri.host == 'icons.duckduckgo.com'
          ? ResponseBody.fromString('', 404)
          : ResponseBody.fromString(
              '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 20">'
              '<rect width="40" height="20" fill="#1464c8"/></svg>',
              200,
              headers: {
                'content-type': ['image/svg+xml'],
              },
            ),
    );
    final client = FaviconClient(adapter: adapter);
    addTearDown(client.dispose);
    final icon = (await client.fetch('example.com'))!;
    final image = img.decodePng(icon)!;
    expect([image.width, image.height], [128, 64]);
    expect(icon.length, lessThanOrEqualTo(64 * 1024));
    expect(await client.fetch('example.com'), same(icon));
    expect(adapter.requests.map((r) => r.uri.toString()), [
      'https://icons.duckduckgo.com/ip3/example.com.ico',
      'https://news.kagi.com/api/favicon-proxy?domain=example.com&quality=best',
    ]);
  });

  test(
    'uses Kagi for empty, invalid, timed out or oversized provider responses',
    () async {
      for (final respond in <FutureOr<ResponseBody> Function(RequestOptions)>[
        (_) => ResponseBody.fromString('', 200),
        (_) => ResponseBody.fromString('<html>Unavailable</html>', 200),
        (_) => ResponseBody.fromBytes(
          img.encodePng(img.Image(width: 16, height: 16, numChannels: 4)),
          200,
        ),
        (_) => ResponseBody.fromString(
          '',
          200,
          headers: {
            'content-length': ['2097153'],
          },
        ),
        (_) => ResponseBody(
          Stream.fromIterable([Uint8List(2097152), Uint8List(1)]),
          200,
        ),
        (request) => throw DioException.receiveTimeout(
          timeout: const Duration(seconds: 5),
          requestOptions: request,
        ),
      ]) {
        final adapter = _Adapter(
          (request) => request.uri.host == 'icons.duckduckgo.com'
              ? respond(request)
              : _pngResponse(),
        );
        final client = FaviconClient(adapter: adapter);
        addTearDown(client.dispose);
        expect(await client.fetch('example.com'), isNotNull);
        expect(adapter.requests, hasLength(2));
      }
    },
  );

  test('tries both providers for each domain in order', () async {
    final adapter = _Adapter(
      (request) => request.uri.queryParameters['domain'] == 'second.example'
          ? _pngResponse()
          : ResponseBody.fromString('', 404),
    );
    final client = FaviconClient(adapter: adapter);
    addTearDown(client.dispose);
    expect(await client.fetch('first.example, second.example'), isNotNull);
    expect(adapter.requests.map((r) => r.uri.toString()), [
      'https://icons.duckduckgo.com/ip3/first.example.ico',
      'https://news.kagi.com/api/favicon-proxy?domain=first.example&quality=best',
      'https://icons.duckduckgo.com/ip3/second.example.ico',
      'https://news.kagi.com/api/favicon-proxy?domain=second.example&quality=best',
    ]);
  });

  test('shares pending downloads across overlapping domain lists', () async {
    final started = Completer<void>();
    final release = Completer<void>();
    final adapter = _Adapter((request) async {
      started.complete();
      await release.future;
      return _pngResponse();
    });
    final client = FaviconClient(adapter: adapter);
    addTearDown(client.dispose);
    final first = client.fetch('example.com, first.example');
    final second = client.fetch('example.com, second.example');
    expect(
      identical(first, client.fetch('example.com, first.example')),
      isTrue,
    );
    await started.future;
    expect(adapter.requests, hasLength(1));
    release.complete();
    final results = await Future.wait([first, second]);
    expect(results.every((icon) => icon != null), isTrue);
    expect(identical(results[0], results[1]), isTrue);
    expect(adapter.requests, hasLength(1));
  });

  test('evicts the least recently used result when the cache fills', () async {
    final adapter = _Adapter((_) => ResponseBody.fromString('', 404));
    final client = FaviconClient(adapter: adapter);
    addTearDown(client.dispose);
    for (var i = 0; i < 128; i++) {
      await client.fetch('site$i.example');
    }
    await client.fetch('site0.example');
    expect(adapter.requests, hasLength(256));
    await client.fetch('new.example');
    await client.fetch('site0.example');
    expect(adapter.requests, hasLength(258));
    await client.fetch('site1.example');
    expect(adapter.requests, hasLength(260));
  });

  test(
    'expires successes after a day and failures after ten minutes',
    () async {
      var now = DateTime.utc(2026);
      final adapter = _Adapter(
        (request) => request.uri.path == '/ip3/found.example.ico'
            ? _pngResponse()
            : ResponseBody.fromString('', 404),
      );
      final client = FaviconClient(adapter: adapter, now: () => now);
      addTearDown(client.dispose);
      expect(await client.fetch('found.example'), isNotNull);
      now = now.add(const Duration(hours: 23));
      expect(await client.fetch('found.example'), isNotNull);
      expect(adapter.requests, hasLength(1));
      now = now.add(const Duration(hours: 1));
      expect(await client.fetch('found.example'), isNotNull);
      expect(adapter.requests, hasLength(2));

      expect(await client.fetch('missing.example'), isNull);
      now = now.add(const Duration(minutes: 9));
      expect(await client.fetch('missing.example'), isNull);
      expect(adapter.requests, hasLength(4));
      now = now.add(const Duration(minutes: 1));
      expect(await client.fetch('missing.example'), isNull);
      expect(adapter.requests, hasLength(6));
    },
  );

  test(
    'revisits a failed primary domain without refetching a cached fallback',
    () async {
      var now = DateTime.utc(2026);
      var primaryAvailable = false;
      final adapter = _Adapter((request) {
        if (request.uri.host == 'news.kagi.com') {
          return ResponseBody.fromString('', 404);
        }
        if (request.uri.path == '/ip3/primary.example.ico') {
          return primaryAvailable
              ? ResponseBody.fromBytes(img.encodePng(_source(32, 32)), 200)
              : ResponseBody.fromString('', 404);
        }
        return _pngResponse();
      });
      final client = FaviconClient(adapter: adapter, now: () => now);
      addTearDown(client.dispose);
      const domains = 'primary.example, fallback.example';
      expect(img.decodePng((await client.fetch(domains))!)!.width, 16);
      expect(adapter.requests, hasLength(3));
      now = now.add(const Duration(minutes: 10));
      expect(img.decodePng((await client.fetch(domains))!)!.width, 16);
      expect(adapter.requests, hasLength(5));
      primaryAvailable = true;
      now = now.add(const Duration(minutes: 10));
      expect(img.decodePng((await client.fetch(domains))!)!.width, 32);
      expect(adapter.requests, hasLength(6));
    },
  );

  test(
    'limits work to four domains and reuses results still waiting for a slot',
    () async {
      var now = DateTime.utc(2026);
      var active = 0, maximum = 0;
      final started = Completer<void>();
      final release = Completer<void>();
      final adapter = _Adapter((_) async {
        active++;
        if (active > maximum) maximum = active;
        if (active == 4 && !started.isCompleted) started.complete();
        await release.future;
        active--;
        return ResponseBody.fromString('', 404);
      });
      final client = FaviconClient(adapter: adapter, now: () => now);
      addTearDown(client.dispose);
      final results = List.generate(7, (i) => client.fetch('site$i.example'));
      await started.future;
      expect(adapter.requests, hasLength(4));
      now = now.add(const Duration(minutes: 2));
      expect(identical(results[6], client.fetch('site6.example')), isTrue);
      release.complete();
      expect(await Future.wait(results), everyElement(isNull));
      expect(maximum, 4);
      expect(adapter.requests, hasLength(14));
    },
  );

  test('never follows provider redirects and caches failed lookups', () async {
    final adapter = _Adapter(
      (_) => ResponseBody.fromString(
        '',
        302,
        headers: {
          'location': ['https://example.com/favicon.ico'],
        },
      ),
    );
    final client = FaviconClient(adapter: adapter);
    addTearDown(client.dispose);
    expect(await client.fetch('example.com'), isNull);
    expect(await client.fetch('example.com'), isNull);
    expect(adapter.requests.map((r) => r.uri.host), [
      'icons.duckduckgo.com',
      'news.kagi.com',
    ]);
  });

  test(
    'clearing cancels pending requests and prevents provider fallback',
    () async {
      final started = Completer<void>();
      final adapter = _Adapter((_) {
        started.complete();
        return Completer<ResponseBody>().future;
      });
      final client = FaviconClient(adapter: adapter);
      addTearDown(client.dispose);
      final pending = client.fetch('example.com');
      await started.future;
      client.clear();
      expect(await pending.timeout(const Duration(seconds: 1)), isNull);
      expect(adapter.requests, hasLength(1));
    },
  );

  test('clearing also cancels domain lists and queued downloads', () async {
    final started = Completer<void>();
    var requests = 0;
    final adapter = _Adapter((_) {
      if (++requests == 4) started.complete();
      return Completer<ResponseBody>().future;
    });
    final client = FaviconClient(adapter: adapter);
    addTearDown(client.dispose);
    final pending = List.generate(
      7,
      (i) => client.fetch('site$i.example, fallback.example'),
    );
    await started.future;
    client.clear();
    expect(
      await Future.wait(pending).timeout(const Duration(seconds: 1)),
      everyElement(isNull),
    );
    expect(adapter.requests, hasLength(4));
  });

  test('clearing cancels an active Kagi fallback', () async {
    final started = Completer<void>();
    final adapter = _Adapter((request) {
      if (request.uri.host == 'icons.duckduckgo.com') {
        return ResponseBody.fromString('', 404);
      }
      started.complete();
      return Completer<ResponseBody>().future;
    });
    final client = FaviconClient(adapter: adapter);
    addTearDown(client.dispose);
    final pending = client.fetch('example.com');
    await started.future;
    client.clear();
    expect(await pending.timeout(const Duration(seconds: 1)), isNull);
    expect(adapter.requests, hasLength(2));
  });
}

img.Image _source(int width, int height) => img.fill(
  img.Image(width: width, height: height, numChannels: 4),
  color: img.ColorRgba8(20, 100, 200, 255),
);

ResponseBody _pngResponse({int size = 16}) =>
    ResponseBody.fromBytes(img.encodePng(_source(size, size)), 200);

class _Adapter implements HttpClientAdapter {
  _Adapter(this.respond);
  final FutureOr<ResponseBody> Function(RequestOptions) respond;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return Future.any([
      Future.sync(() => respond(options)),
      if (cancelFuture != null)
        cancelFuture.then(
          (_) => throw DioException.requestCancelled(
            requestOptions: options,
            reason: 'cancelled',
          ),
        ),
    ]);
  }

  @override
  void close({bool force = false}) {}
}
