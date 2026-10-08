import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:ente_crypto_api/ente_crypto_api.dart';
import 'package:logging/logging.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:pointycastle/export.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

typedef FaviconCacheKey = ({String id, Uint8List encryptionKey});
typedef CachedFavicon = ({Uint8List? bytes, DateTime expires});

class FaviconCache {
  final _logger = Logger('FaviconCache');
  Future<Database>? _database;
  int _generation = 0;

  static FaviconCacheKey key(String domains, Uint8List dataKey) {
    final mac = HMac(SHA256Digest(), 64)..init(KeyParameter(dataKey));
    return (
      id: CryptoUtil.bin2hex(
        mac.process(Uint8List.fromList(utf8.encode('favicon:id:$domains'))),
      ),
      encryptionKey: mac.process(
        Uint8List.fromList(utf8.encode('favicon:key:$domains')),
      ),
    );
  }

  Future<Database> _open() async {
    final directory = await getApplicationCacheDirectory();
    await directory.create(recursive: true);
    if (Platform.isWindows || Platform.isLinux) sqfliteFfiInit();
    final factory = Platform.isWindows || Platform.isLinux
        ? databaseFactoryFfi
        : databaseFactory;
    return factory.openDatabase(
      path.join(directory.path, 'favicons.db'),
      options: OpenDatabaseOptions(
        version: 1,
        onCreate: (db, _) => db.execute('''
          CREATE TABLE icons (
            id TEXT PRIMARY KEY,
            expires INTEGER NOT NULL,
            header BLOB NOT NULL,
            data BLOB NOT NULL
          )
        '''),
      ),
    );
  }

  Future<CachedFavicon?> read(FaviconCacheKey key) async {
    try {
      final db = await (_database ??= _open());
      final now = DateTime.now().millisecondsSinceEpoch;
      final rows = await db.query(
        'icons',
        where: 'id = ? AND expires > ?',
        whereArgs: [key.id, now],
      );
      if (rows.isEmpty) return null;
      final row = rows.single;
      final bytes = await CryptoUtil.decryptData(
        row['data'] as Uint8List,
        key.encryptionKey,
        row['header'] as Uint8List,
      );
      return (
        bytes: bytes,
        expires: DateTime.fromMillisecondsSinceEpoch(row['expires'] as int),
      );
    } catch (error, stack) {
      _logger.warning('Could not read icon cache', error, stack);
      return null;
    }
  }

  Future<void> write(
    FaviconCacheKey key,
    Uint8List bytes,
    DateTime expires,
  ) async {
    final generation = _generation;
    try {
      final db = await (_database ??= _open());
      final encrypted = await CryptoUtil.encryptData(bytes, key.encryptionKey);
      await db.transaction((txn) async {
        if (generation != _generation) return;
        final now = DateTime.now().millisecondsSinceEpoch;
        await txn.insert('icons', {
          'id': key.id,
          'data': encrypted.encryptedData!,
          'header': encrypted.header!,
          'expires': expires.millisecondsSinceEpoch,
        }, conflictAlgorithm: ConflictAlgorithm.replace);
        await txn.delete('icons', where: 'expires <= ?', whereArgs: [now]);
        await txn.execute('''
          DELETE FROM icons WHERE id IN (
            SELECT id FROM icons ORDER BY expires DESC LIMIT -1 OFFSET 1024
          )
        ''');
      });
    } catch (error, stack) {
      _logger.warning('Could not write icon cache', error, stack);
    }
  }

  Future<void> clear() async {
    _generation++;
    try {
      final db = await (_database ??= _open());
      await db.delete('icons');
    } catch (error, stack) {
      _logger.warning('Could not clear icon cache', error, stack);
    }
  }
}
