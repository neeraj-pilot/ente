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
  static final noticeNeeded = ValueNotifier(false);

  Future<void> _tail = Future.value();
  bool _locked = true;
  bool _declined = false;

  static void initialize() {
    if (!Platform.isIOS) return;
    final service = _instance = AutoFillService._();
    WidgetsBinding.instance.addObserver(service);
    Bus.instance.on<CodesUpdatedEvent>().listen((_) => service._refresh());
    service._refresh();
  }

  static Future<void> clear() async =>
      _instance?._enqueue(() => _channel.invokeMethod<void>('clear'));

  static void appLockChanged(bool locked) {
    final service = _instance;
    if (service == null) return;
    service._locked = locked;
    if (!locked) service._refresh();
  }

  static void answerNotice({required bool share}) {
    final service = _instance;
    if (service == null) return;
    noticeNeeded.value = false;
    service._declined = !share;
    if (share) {
      unawaited(
        service._enqueue(() async {
          await PreferenceService.instance.setAutoFillAccepted(true);
          await service._sync();
        }),
      );
    }
  }

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
    final prefs = PreferenceService.instance;
    if (!hasVault || await _channel.invokeMethod<bool>('isEnabled') != true) {
      _declined = false;
      noticeNeeded.value = false;
      await prefs.setAutoFillAccepted(false);
      await _channel.invokeMethod('clear');
      return;
    }
    if (!prefs.autoFillAccepted()) {
      if (!_locked && !_declined) noticeNeeded.value = true;
      await _channel.invokeMethod('clear');
      return;
    }
    final codes = await CodeStore.instance.getAllCodes(sortCodes: false);
    sortAuthCodes(codes, prefs.codeSortKey());
    await _channel.invokeMethod('publish', WatchSnapshot.fromCodes(codes).data);
  }
}
