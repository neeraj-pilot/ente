import 'dart:async';
import 'dart:io';

import 'package:ente_auth/core/configuration.dart';
import 'package:ente_auth/events/codes_updated_event.dart';
import 'package:ente_auth/models/watch/snapshot.dart';
import 'package:ente_auth/services/preference_service.dart';
import 'package:ente_auth/store/code_store.dart';
import 'package:ente_auth/utils/code_sort.dart';
import 'package:ente_events/event_bus.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:logging/logging.dart';

class AutoFillService with WidgetsBindingObserver {
  AutoFillService._();

  static const _channel = MethodChannel('io.ente.auth/autofill');
  static final _logger = Logger('AutoFillService');
  static AutoFillService? _instance;

  Future<void> _tail = Future.value();

  static void initialize() {
    if (!Platform.isIOS) return;
    final service = _instance = AutoFillService._();
    WidgetsBinding.instance.addObserver(service);
    Bus.instance.on<CodesUpdatedEvent>().listen((_) => service._refresh());
    service._refresh();
  }

  static Future<void> clear() async =>
      _instance?._enqueue(() => _channel.invokeMethod<void>('clear'));

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  void _refresh() => unawaited(_enqueue(_sync));

  Future<void> _enqueue(Future<void> Function() action) =>
      _tail = _tail.then((_) => action()).catchError((Object e, StackTrace s) {
        _logger.warning('Could not update AutoFill codes', e, s);
      });

  Future<void> _sync() async {
    final config = Configuration.instance;
    final hasVault =
        config.hasConfiguredAccount() || config.hasOptedForOfflineMode();
    if (!hasVault || await _channel.invokeMethod<bool>('isEnabled') != true) {
      await _channel.invokeMethod('clear');
      return;
    }
    final codes = await CodeStore.instance.getAllCodes(sortCodes: false);
    sortAuthCodes(codes, PreferenceService.instance.codeSortKey());
    await _channel.invokeMethod('publish', WatchSnapshot.fromCodes(codes).data);
  }
}
