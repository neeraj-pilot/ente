import 'dart:convert';
import 'dart:ffi';
import 'dart:isolate';

import 'package:ffi/ffi.dart';
import 'package:ffmpeg/src/bindings.dart';

class Result {
  const Result(this.returnCode, this.output);

  final int returnCode;
  final String output;

  bool get isSuccess => returnCode == 0;
  bool get isCancelled => returnCode == 255;
}

abstract interface class Session {
  factory Session(void Function(Duration) onProgress) = _NativeSession;

  Future<Result> execute(List<String> arguments);
  void cancel();
}

final class _NativeSession implements Session {
  _NativeSession(void Function(Duration) onProgress) {
    final bindings = Bindings.instance;
    void progress(Pointer<Void> opaque, int time, int isFinal) {
      if (!_finished && time >= 0) {
        onProgress(Duration(microseconds: time));
      }
    }

    _callback = NativeCallable<ProgressCallback>.listener(progress);
    _session = bindings.sessionNew(_callback.nativeFunction, nullptr);
    if (_session == nullptr) {
      _callback.close();
      throw StateError('Could not allocate an FFmpeg session.');
    }
  }

  late final NativeCallable<ProgressCallback> _callback;
  late final Pointer<SessionHandle> _session;
  bool _started = false;
  bool _finished = false;

  @override
  Future<Result> execute(List<String> arguments) async {
    if (_started) throw StateError('An FFmpeg session runs once.');
    _started = true;
    try {
      _checkStrings(arguments);
      return await _executeInWorker(_session.address, arguments);
    } finally {
      _finished = true;
      _callback.close();
      Bindings.instance.sessionFree(_session);
    }
  }

  @override
  void cancel() {
    if (!_finished) Bindings.instance.cancel(_session);
  }
}

Future<Result> _executeInWorker(int address, List<String> arguments) =>
    Isolate.run(() => _execute(address, arguments));

Result _execute(int address, List<String> arguments) {
  return using((arena) {
    final values = ['ffmpeg', ...arguments];
    final argv = arena<Pointer<Utf8>>(values.length + 1);
    for (var i = 0; i < values.length; i++) {
      argv[i] = values[i].toNativeUtf8(allocator: arena);
    }
    final bindings = Bindings.instance;
    final session = Pointer<SessionHandle>.fromAddress(address);
    final code = bindings.execute(session, values.length, argv);
    final output = bindings.sessionOutput(session);
    return Result(
      code,
      utf8.decode(
        output.cast<Uint8>().asTypedList(output.length),
        allowMalformed: true,
      ),
    );
  });
}

Future<Map<String, dynamic>> probeMedia(String path) {
  _checkStrings([path]);
  return Isolate.run(() => _probe(path));
}

Map<String, dynamic> _probe(String path) {
  return using((arena) {
    final bindings = Bindings.instance;
    final output = arena<Pointer<Utf8>>();
    final code = bindings.probe(path.toNativeUtf8(allocator: arena), output);
    try {
      if (code < 0) return {};
      return jsonDecode(output.value.toDartString()) as Map<String, dynamic>;
    } finally {
      bindings.freeString(output.value);
    }
  });
}

void _checkStrings(List<String> values) {
  if (values.any((value) => value.contains('\u0000'))) {
    throw ArgumentError('FFmpeg arguments cannot contain NUL characters.');
  }
}
