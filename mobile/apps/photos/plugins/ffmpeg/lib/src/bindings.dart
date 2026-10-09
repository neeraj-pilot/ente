import 'dart:ffi';
import 'dart:io';

import 'package:ffi/ffi.dart';

final class SessionHandle extends Opaque {}

typedef ProgressCallback = Void Function(Pointer<Void>, Int64, Int32);

final class Bindings {
  Bindings(DynamicLibrary library)
    : sessionNew = library
          .lookupFunction<
            Pointer<SessionHandle> Function(
              Pointer<NativeFunction<ProgressCallback>>,
              Pointer<Void>,
            ),
            Pointer<SessionHandle> Function(
              Pointer<NativeFunction<ProgressCallback>>,
              Pointer<Void>,
            )
          >('ffmpeg_session_new'),
      sessionFree = library
          .lookupFunction<
            Void Function(Pointer<SessionHandle>),
            void Function(Pointer<SessionHandle>)
          >('ffmpeg_session_free'),
      sessionOutput = library
          .lookupFunction<
            Pointer<Utf8> Function(Pointer<SessionHandle>),
            Pointer<Utf8> Function(Pointer<SessionHandle>)
          >('ffmpeg_session_output'),
      execute = library
          .lookupFunction<
            Int32 Function(
              Pointer<SessionHandle>,
              Int32,
              Pointer<Pointer<Utf8>>,
            ),
            int Function(Pointer<SessionHandle>, int, Pointer<Pointer<Utf8>>)
          >('ffmpeg_execute'),
      cancel = library
          .lookupFunction<
            Void Function(Pointer<SessionHandle>),
            void Function(Pointer<SessionHandle>)
          >('ffmpeg_cancel'),
      probe = library
          .lookupFunction<
            Int32 Function(Pointer<Utf8>, Pointer<Pointer<Utf8>>),
            int Function(Pointer<Utf8>, Pointer<Pointer<Utf8>>)
          >('ffmpeg_probe_media_json'),
      freeString = library
          .lookupFunction<
            Void Function(Pointer<Utf8>),
            void Function(Pointer<Utf8>)
          >('ffmpeg_free_string');

  static final instance = Bindings(_openLibrary());

  final Pointer<SessionHandle> Function(
    Pointer<NativeFunction<ProgressCallback>>,
    Pointer<Void>,
  )
  sessionNew;
  final void Function(Pointer<SessionHandle>) sessionFree;
  final Pointer<Utf8> Function(Pointer<SessionHandle>) sessionOutput;
  final int Function(Pointer<SessionHandle>, int, Pointer<Pointer<Utf8>>)
  execute;
  final void Function(Pointer<SessionHandle>) cancel;
  final int Function(Pointer<Utf8>, Pointer<Pointer<Utf8>>) probe;
  final void Function(Pointer<Utf8>) freeString;

  static DynamicLibrary _openLibrary() {
    if (Platform.isAndroid) return DynamicLibrary.open('libffmpeg_runtime.so');
    if (Platform.isIOS) {
      return DynamicLibrary.open('FFmpegRuntime.framework/FFmpegRuntime');
    }
    throw UnsupportedError('FFmpeg supports Android and iOS.');
  }
}
