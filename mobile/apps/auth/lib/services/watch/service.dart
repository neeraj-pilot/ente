import 'dart:async';
import 'dart:io';

import 'package:ente_auth/core/configuration.dart';
import 'package:ente_auth/events/codes_updated_event.dart';
import 'package:ente_auth/models/code.dart';
import 'package:ente_auth/models/watch/entry.dart';
import 'package:ente_auth/models/watch/state.dart';
import 'package:ente_auth/services/watch/selection.dart';
import 'package:ente_auth/store/code_store.dart';
import 'package:ente_events/event_bus.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

class WatchService extends ChangeNotifier with WidgetsBindingObserver {
  WatchService._(this._preferences);

  static const _preferenceKey = 'watchSelection';
  static const _channel = MethodChannel('io.ente.auth/watch');
  static const _events = EventChannel('io.ente.auth/watch/state');
  static WatchService? _instance;
  static WatchService get instance => _instance!;

  static Future<void> initialize() async {
    if (!Platform.isIOS && !Platform.isAndroid) return;
    final service = WatchService._(await SharedPreferences.getInstance());
    _instance = service;
    await service._start();
  }

  static void appLockChanged(bool locked) => _instance?._setLocked(locked);

  // Also used by logout from a background isolate, where no UI service exists.
  static Future<void> clearBeforeLogout() async {
    if (!Platform.isIOS && !Platform.isAndroid) return;
    try {
      if (_instance case final service?) {
        await service._clearForLogout();
      } else {
        await _clearNative(await SharedPreferences.getInstance());
      }
    } on MissingPluginException {
      // Older OS versions and builds without watch support have no channel.
    } on Object {
      // A watch failure must not retain the phone's signed-in vault. Base logout
      // clears preferences; the absent root forces reset before the next export.
      _instance?._failed('watchUnavailable');
    }
  }

  final SharedPreferences _preferences;

  String vaultScope() {
    final config = Configuration.instance;
    if (config.hasConfiguredAccount()) return 'account:${config.getUserID()}';
    return config.hasOptedForOfflineMode() ? 'offline' : 'none';
  }

  WatchState state = const WatchState();
  String? errorCode;
  int omittedCount = 0;
  WatchSelection? _selection;
  bool _available = false;
  bool _locked = true;
  bool _foreground = false;
  int _generation = 0;
  Future<void> _tail = Future.value();
  Timer? _debounce;
  Completer<void>? _resumed;

  Set<int> get selectedIDs =>
      _selection?.watches[state.enrollmentID]?.keys.toSet() ?? {};

  Future<void> _start() async {
    _foreground =
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    WidgetsBinding.instance.addObserver(this);
    try {
      _accept(await _call('state'));
      _available = true;
      _events.receiveBroadcastStream().listen((dynamic value) {
        final next = WatchState.fromMap(value as Map<Object?, Object?>);
        final changed =
            next.enrollmentID != state.enrollmentID ||
            next.requestID != state.requestID ||
            next.vaultEpoch != state.vaultEpoch;
        _accept(next);
        if (changed) _schedule();
      }, onError: (Object _) => _failed('watchUnavailable'));
      Bus.instance.on<CodesUpdatedEvent>().listen((_) => _schedule());
      await refresh();
    } on MissingPluginException {
      _available = false;
    } on Object {
      _failed('watchUnavailable');
    }
  }

  static Future<WatchState> _call(
    String method, [
    Map<String, Object?>? arguments,
  ]) async {
    final value = await _channel.invokeMapMethod<Object?, Object?>(
      method,
      arguments,
    );
    return WatchState.fromMap(value!);
  }

  void _accept(WatchState next) {
    state = next;
    notifyListeners();
  }

  void _failed(String code) {
    errorCode = code;
    notifyListeners();
  }

  Future<T> _enqueue<T>(Future<T> Function() operation) {
    final result = _tail.then((_) => operation());
    _tail = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  void _schedule() {
    _generation++;
    _debounce?.cancel();
    _debounce = Timer(
      const Duration(milliseconds: 150),
      () => unawaited(refresh()),
    );
  }

  void _setLocked(bool locked) {
    _locked = locked;
    _schedule();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState lifecycle) {
    _foreground = lifecycle == AppLifecycleState.resumed;
    if (_foreground) {
      _resumed?.complete();
      _resumed = null;
    }
    _schedule();
  }

  Future<void> refresh() => _enqueue(() async {
    if (!_available) return;
    try {
      errorCode = null;
      _accept(await _call('state'));
      if (_locked || !_foreground || state.vaultEpoch == null) return;
      await _reconcile();
      final generation = _generation;
      final scope = vaultScope();
      if (scope == 'none' ||
          !_current(generation, scope) ||
          !state.canPublish) {
        return;
      }
      final id = state.enrollmentID!;
      final epoch = state.vaultEpoch!;
      final selection = Map<int, String>.of(_selection!.watches[id] ?? {});
      // The vault is read only here, during an unlocked foreground session.
      final codes = selection.isEmpty
          ? <Code>[]
          : await CodeStore.instance.getAllCodes(
              sortCodes: false,
              requireCompleteRead: true,
            );
      if (!_current(generation, scope) ||
          state.enrollmentID != id ||
          state.vaultEpoch != epoch) {
        return;
      }
      final snapshot = WatchSnapshot.fromCodes(codes, selection);
      _accept(
        await _call('publish', {
          'enrollmentID': id,
          'vaultEpoch': epoch,
          'snapshot': snapshot.encode(),
        }),
      );
      omittedCount = snapshot.omittedIDs.length;
      errorCode = null;
      notifyListeners();
    } on PlatformException catch (error) {
      _failed(error.code);
    } on Object {
      // Vault and decoder errors may contain secrets; never log their values.
      _failed('watchUnavailable');
    }
  });

  bool _current(int generation, String scope) =>
      !_locked &&
      _foreground &&
      generation == _generation &&
      scope == vaultScope();

  Future<void> _save(WatchSelection selection) async {
    if (!await _preferences.setString(_preferenceKey, selection.encode())) {
      throw StateError('Could not save watch selection.');
    }
    _selection = selection;
  }

  Future<void> _reconcile() async {
    await _preferences.reload();
    final scope = vaultScope();
    final stored = WatchSelection.decode(
      _preferences.getString(_preferenceKey),
    );
    if (stored == null || stored.scope != scope) {
      await _clear();
      await _save(WatchSelection(scope: scope, epoch: state.vaultEpoch!));
    } else if (stored.epoch != state.vaultEpoch) {
      await _save(WatchSelection(scope: scope, epoch: state.vaultEpoch!));
    } else {
      _selection = stored;
    }
  }

  static Future<WatchState> _clearNative(SharedPreferences preferences) async {
    // An absent selection requires a reset before the next export, including
    // after a native failure or a logout from another isolate.
    if (!await preferences.remove(_preferenceKey)) {
      throw StateError('Could not clear watch selection.');
    }
    return _call('clear');
  }

  Future<void> _clear() async {
    _selection = null;
    omittedCount = 0;
    _accept(await _clearNative(_preferences));
  }

  Future<void> _clearForLogout() {
    _generation++;
    return _enqueue(() async {
      try {
        await _clear();
      } on MissingPluginException {
        // No watch capability exists on this platform or OS version.
      }
    });
  }

  // The UI must authenticate the user immediately before these actions.
  Future<void> approve(WatchState target, String scope) => _enqueue(() async {
    await _prepareChange(target, scope);
    _accept(await _call('approve', {'enrollmentID': target.requestID}));
    _schedule();
  });

  Future<void> select(WatchState target, String scope, Set<int> ids) =>
      _enqueue(() async {
        await _prepareChange(target, scope);
        final enrollmentID = target.enrollmentID!;
        if (!state.canPublish || state.enrollmentID != enrollmentID) {
          throw PlatformException(code: 'wrongEnrollment');
        }
        final previous = _selection!.watches[enrollmentID] ?? {};
        _selection!.watches[enrollmentID] = {
          for (final id in ids) id: previous[id] ?? const Uuid().v4(),
        };
        await _save(_selection!);
        _schedule();
      });

  Future<void> disable(WatchState target, String scope) => _enqueue(() async {
    await _prepareChange(target, scope);
    _accept(await _call('disable', {'enrollmentID': target.enrollmentID}));
    _selection!.watches.remove(target.enrollmentID);
    await _save(_selection!);
  });

  Future<void> _prepareChange(WatchState target, String scope) async {
    if (!_foreground && !_locked) {
      _resumed ??= Completer<void>();
      await _resumed!.future.timeout(
        const Duration(seconds: 2),
        onTimeout: () => throw PlatformException(code: 'phoneLocked'),
      );
    }
    if (_locked || !_foreground) throw PlatformException(code: 'phoneLocked');
    _accept(await _call('state'));
    if (state.vaultEpoch == null) {
      throw PlatformException(code: 'watchUnavailable');
    }
    await _reconcile();
    if (_locked || !_foreground) {
      throw PlatformException(code: 'phoneLocked');
    }
    if (scope != vaultScope() ||
        target.vaultEpoch != state.vaultEpoch ||
        target.enrollmentID != state.enrollmentID ||
        target.requestID != state.requestID) {
      throw PlatformException(code: 'wrongEnrollment');
    }
  }
}
