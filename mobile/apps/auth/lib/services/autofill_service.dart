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

  Future<void> _tail = Future.value();

  static void initialize() {
    if (!Platform.isIOS) return;
    final service = AutoFillService._();
    WidgetsBinding.instance.addObserver(service);
    Bus.instance.on<CodesUpdatedEvent>().listen((_) => service._refresh());
    service._refresh();
  }

  static Future<void> clearBeforeLogout() async {
    if (!Platform.isIOS) return;
    try {
      await _channel.invokeMethod('clear');
    } on Object catch (e, s) {
      _logger.warning('Could not clear AutoFill codes', e, s);
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  void _refresh() {
    _tail = _tail.then((_) => _sync()).catchError((Object e, StackTrace s) {
      _logger.warning('Could not update AutoFill codes', e, s);
    });
  }

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
