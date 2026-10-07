import "dart:async";
import "dart:convert";
import "dart:io";
import "dart:typed_data";

import "package:flutter/material.dart";
import "package:flutter_test/flutter_test.dart";
import "package:integration_test/integration_test.dart";
import "package:native_video_player/native_video_player.dart";
import "package:path_provider/path_provider.dart";
import "package:photo_manager/photo_manager.dart";
import "package:photos/ui/viewer/file/external_video_viewer.dart";
import "package:photos/ui/viewer/file/native_video_player_controls/play_pause_button.dart";

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  late File videoFile;

  setUp(() async {
    final directory = await getTemporaryDirectory();
    videoFile = File("${directory.path}/external_video_viewer_test.mp4");
    await videoFile.writeAsBytes(base64Decode(_portraitVideo));
  });

  tearDown(() async {
    if (await videoFile.exists()) await videoFile.delete();
  });

  testWidgets("shared file controls, looping, lifecycle, and fullscreen", (
    tester,
  ) async {
    final state = await _openVideo(tester, videoFile.path);
    final controller = state.controller!;
    expect(controller.videoInfo!.width, 320);
    expect(controller.videoInfo!.height, 640);
    final videoSize = tester.getSize(find.byType(NativeVideoPlayerView));
    expect(videoSize.width / videoSize.height, closeTo(0.5, 0.01));

    await _showControls(tester);
    await tester.tap(find.byType(PlayPauseButton));
    await _pumpUntil(
      tester,
      () => controller.playbackStatus == PlaybackStatus.paused,
    );
    await tester.tap(find.byIcon(Icons.volume_up));
    await _pumpUntil(tester, () => controller.volume == 0);
    expect(find.byIcon(Icons.volume_off), findsOneWidget);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 100)),
    );
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump(const Duration(milliseconds: 300));
    expect(controller.playbackStatus, PlaybackStatus.paused);

    final slider = tester.getRect(find.byType(Slider));
    await tester.tapAt(
      Offset(slider.left + slider.width * 0.75, slider.center.dy),
    );
    await _pumpUntil(
      tester,
      () => controller.playbackPosition >= const Duration(seconds: 2),
    );
    expect(controller.playbackStatus, PlaybackStatus.paused);

    var reachedEnd = false;
    final endingSubscription = controller.events.listen((event) {
      if (event is PlaybackEndedEvent) reachedEnd = true;
    });
    await controller.seekTo(const Duration(milliseconds: 3500));
    await _pumpUntil(
      tester,
      () => controller.playbackPosition >= const Duration(seconds: 3),
    );
    await tester.tap(find.byType(PlayPauseButton));
    await _pumpUntil(
      tester,
      () =>
          reachedEnd &&
          controller.playbackStatus == PlaybackStatus.playing &&
          controller.playbackPosition < const Duration(seconds: 1),
    );
    await endingSubscription.cancel();

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await _pumpUntil(
      tester,
      () => controller.playbackStatus == PlaybackStatus.paused,
    );
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await _pumpUntil(
      tester,
      () => controller.playbackStatus == PlaybackStatus.playing,
    );

    await _showControls(tester);
    await tester.tap(find.byIcon(Icons.fullscreen));
    await _pumpUntil(
      tester,
      () =>
          state.isFullscreen &&
          find.byType(AppBar).evaluate().isEmpty &&
          (Platform.isIOS ||
              MediaQuery.orientationOf(
                    tester.element(find.byType(ExternalVideoViewer)),
                  ) ==
                  Orientation.landscape),
    );
    expect(find.byType(AppBar), findsNothing);
    expect(state.controller, same(controller));
    await tester.binding.handlePopRoute();
    await _pumpUntil(
      tester,
      () =>
          !state.isFullscreen &&
          find.byType(AppBar).evaluate().isNotEmpty &&
          (Platform.isIOS ||
              MediaQuery.orientationOf(
                    tester.element(find.byType(ExternalVideoViewer)),
                  ) ==
                  Orientation.portrait),
    );
    expect(find.byType(AppBar), findsOneWidget);
    expect(controller.playbackStatus, PlaybackStatus.playing);
    expect(state.controller, same(controller));

    final closed = Completer<void>();
    final subscription = controller.events.listen(
      (_) {},
      onDone: closed.complete,
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await _pumpUntil(tester, () => closed.isCompleted);
    await subscription.cancel();
    expect(tester.takeException(), isNull);
  });

  testWidgets("plays an Android content URI without a filesystem path", (
    tester,
  ) async {
    AssetEntity? asset;
    await PhotoManager.setIgnorePermissionCheck(true);
    try {
      asset = await PhotoManager.editor.saveVideo(
        videoFile,
        title: "external_video_viewer_test.mp4",
        relativePath: "Movies/EnteExternalVideoTest",
      );
      final uri = await asset.getMediaUrl();
      expect(uri, startsWith("content://"));
      final state = await _openVideo(tester, uri!);
      expect(state.controller!.videoInfo!.width, 320);
      expect(state.controller!.videoInfo!.height, 640);
      expect(tester.takeException(), isNull);
    } finally {
      await tester.pumpWidget(const SizedBox.shrink());
      if (asset != null) {
        // Deleting our own file avoids Android's native confirmation dialog.
        final createdFile = await asset.originFile;
        if (createdFile != null && await createdFile.exists()) {
          await createdFile.delete();
        }
        if (await asset.exists) {
          await PhotoManager.editor.deleteWithIds([asset.id]);
        }
      }
      await PhotoManager.setIgnorePermissionCheck(false);
    }
  }, skip: !Platform.isAndroid);

  testWidgets("preserves the display aspect ratio of a rotated iOS video", (
    tester,
  ) async {
    final bytes = base64Decode(_portraitVideo);
    final trackHeader = String.fromCharCodes(bytes).indexOf("tkhd");
    expect(trackHeader, greaterThan(0));
    // A quarter-turn in the MP4 track matrix keeps encoded pixels unchanged.
    final matrix = ByteData.sublistView(bytes, trackHeader + 44);
    matrix.setInt32(0, 0);
    matrix.setInt32(4, 65536);
    matrix.setInt32(12, -65536);
    matrix.setInt32(16, 0);
    await videoFile.delete();
    videoFile = File(
      "${videoFile.parent.path}/external_video_viewer_rotated_test.mp4",
    );
    await videoFile.writeAsBytes(bytes);
    await _openVideo(tester, videoFile.uri.toString());
    final size = tester.getSize(find.byType(NativeVideoPlayerView));
    expect(size.width / size.height, closeTo(2, 0.01));
    await tester.pumpWidget(const SizedBox.shrink());
    expect(tester.takeException(), isNull);
  }, skip: !Platform.isIOS);

  testWidgets("shows an error when an external video cannot be opened", (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: _TestViewer(uri: "${videoFile.path}.missing")),
    );
    await _pumpUntil(
      tester,
      () => find.byIcon(Icons.error_outline).evaluate().isNotEmpty,
    );
    expect(find.byType(NativeVideoPlayerView), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
    expect(tester.takeException(), isNull);
  });
}

class _TestViewer extends StatefulWidget {
  final String uri;

  const _TestViewer({required this.uri});

  @override
  State<_TestViewer> createState() => _TestViewerState();
}

class _TestViewerState extends State<_TestViewer> {
  final _videoKey = GlobalKey<ExternalVideoViewerState>();
  bool _fullscreen = false;

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_fullscreen,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _fullscreen) {
          unawaited(_videoKey.currentState!.exitFullscreen());
        }
      },
      child: Scaffold(
        appBar: _fullscreen ? null : AppBar(),
        body: ExternalVideoViewer(
          key: _videoKey,
          uri: widget.uri,
          onFullscreenChanged: (fullscreen) =>
              setState(() => _fullscreen = fullscreen),
        ),
      ),
    );
  }
}

Future<ExternalVideoViewerState> _openVideo(
  WidgetTester tester,
  String uri,
) async {
  await tester.pumpWidget(
    MaterialApp(
      key: ValueKey(uri),
      home: _TestViewer(uri: uri),
    ),
  );
  final state = tester.state<ExternalVideoViewerState>(
    find.byType(ExternalVideoViewer),
  );
  await _pumpUntil(
    tester,
    () =>
        state.controller?.playbackStatus == PlaybackStatus.playing &&
        state.controller?.videoInfo != null &&
        state.controller!.playbackPosition > Duration.zero,
  );
  return state;
}

Future<void> _showControls(WidgetTester tester) async {
  final opacity = tester.widget<AnimatedOpacity>(
    find.descendant(
      of: find.byType(ExternalVideoViewer),
      matching: find.byType(AnimatedOpacity),
    ),
  );
  if (opacity.opacity == 0) {
    final bounds = tester.getRect(find.byType(ExternalVideoViewer));
    await tester.tapAt(bounds.topLeft + const Offset(20, 20));
  }
  await tester.pump(const Duration(milliseconds: 250));
}

Future<void> _pumpUntil(WidgetTester tester, bool Function() condition) async {
  final deadline = DateTime.now().add(const Duration(seconds: 20));
  while (!condition() && DateTime.now().isBefore(deadline)) {
    if (tester.binding.lifecycleState == AppLifecycleState.paused) {
      // A paused iOS engine cannot deliver the frame tester.pump waits for.
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 100)),
      );
    } else {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }
  expect(condition(), isTrue, reason: "Timed out waiting for video playback");
}

// Generated with FFmpeg: 4 seconds of green, 320x640, 30 fps, H.264 baseline/yuv420p.
const _portraitVideo =
    "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAUEbW9vdgAAAGxtdmhkAAAAAAAA"
    "AAAAAAAAAAAD6AAAD6AAAQAAAQAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAA"
    "AAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAABC90cmFrAAAAXHRr"
    "aGQAAAADAAAAAAAAAAAAAAABAAAAAAAAD6AAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAA"
    "AAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAUAAAAKAAAAAAAAkZWR0cwAAABxlbHN0AAAAAAAA"
    "AAEAAA+gAAAAAAABAAAAAAOnbWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAAA8AAAA8ABVxAAA"
    "AAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRlb0hhbmRsZXIAAAADUm1pbmYA"
    "AAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAA"
    "AQAAAxJzdGJsAAAAunN0c2QAAAAAAAAAAQAAAKphdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAA"
    "AAAAAUACgABIAAAASAAAAAAAAAABFExhdmM2MC4zLjEwMCBsaWJ4MjY0AAAAAAAAAAAAAAAA"
    "GP//AAAAMGF2Y0MBQsAe/+EAGGdCwB7ZAUBRsBEAAAMAAQAAAwA8DxYuSAEABWjLg8sgAAAA"
    "EHBhc3AAAAABAAAAAQAAABRidHJ0AAAAAAAAFA4AABQOAAAAGHN0dHMAAAAAAAAAAQAAAHgA"
    "AAIAAAAAFHN0c3MAAAAAAAAAAQAAAAEAAAAcc3RzYwAAAAAAAAABAAAAAQAAAHgAAAABAAAB"
    "9HN0c3oAAAAAAAAAAAAAAHgAAATpAAAACwAAAAwAAAALAAAACwAAAAsAAAALAAAACwAAAAsA"
    "AAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAA"
    "CwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsA"
    "AAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAA"
    "CwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsA"
    "AAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAA"
    "CwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsA"
    "AAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAA"
    "CwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsA"
    "AAALAAAACwAAAAsAAAAUc3RjbwAAAAAAAAABAAAFNAAAAGF1ZHRhAAAAWW1ldGEAAAAAAAAA"
    "IWhkbHIAAAAAAAAAAG1kaXJhcHBsAAAAAAAAAAAAAAAALGlsc3QAAAAkqXRvbwAAABxkYXRh"
    "AAAAAQAAAABMYXZmNjAuMy4xMDAAAAAIZnJlZQAACg9tZGF0AAACcgYF//9u3EXpvebZSLeW"
    "LNgg2SPu73gyNjQgLSBjb3JlIDE2NCByMzA3NSA2NmE1YmMxIC0gSC4yNjQvTVBFRy00IEFW"
    "QyBjb2RlYyAtIENvcHlsZWZ0IDIwMDMtMjAyMSAtIGh0dHA6Ly93d3cudmlkZW9sYW4ub3Jn"
    "L3gyNjQuaHRtbCAtIG9wdGlvbnM6IGNhYmFjPTAgcmVmPTMgZGVibG9jaz0xOjA6MCBhbmFs"
    "eXNlPTB4MToweDExMSBtZT1oZXggc3VibWU9NyBwc3k9MSBwc3lfcmQ9MS4wMDowLjAwIG1p"
    "eGVkX3JlZj0xIG1lX3JhbmdlPTE2IGNocm9tYV9tZT0xIHRyZWxsaXM9MSA4eDhkY3Q9MCBj"
    "cW09MCBkZWFkem9uZT0yMSwxMSBmYXN0X3Bza2lwPTEgY2hyb21hX3FwX29mZnNldD0tMiB0"
    "aHJlYWRzPTE4IGxvb2thaGVhZF90aHJlYWRzPTMgc2xpY2VkX3RocmVhZHM9MCBucj0wIGRl"
    "Y2ltYXRlPTEgaW50ZXJsYWNlZD0wIGJsdXJheV9jb21wYXQ9MCBjb25zdHJhaW5lZF9pbnRy"
    "YT0wIGJmcmFtZXM9MCB3ZWlnaHRwPTAga2V5aW50PTI1MCBrZXlpbnRfbWluPTI1IHNjZW5l"
    "Y3V0PTQwIGludHJhX3JlZnJlc2g9MCByY19sb29rYWhlYWQ9NDAgcmM9Y3JmIG1idHJlZT0x"
    "IGNyZj0yMy4wIHFjb21wPTAuNjAgcXBtaW49MCBxcG1heD02OSBxcHN0ZXA9NCBpcF9yYXRp"
    "bz0xLjQwIGFxPTE6MS4wMACAAAACb2WIhAvxGKAAJHMcAARBY4AAiqycnJycnJycnJycnJyc"
    "nJycnJyddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddeAAAA"
    "B0GaOBfgDIYAAAAIQZpUBfgDIYAAAAAHQZpgL8AZDAAAAAdBmoAvwBkMAAAAB0GaoC/AGQwA"
    "AAAHQZrAL8AZDAAAAAdBmuAvwBkMAAAAB0GbAC/AGQwAAAAHQZsgL8AZDAAAAAdBm0AvwBkM"
    "AAAAB0GbYC/AGQwAAAAHQZuAL8AZDAAAAAdBm6AvwBkMAAAAB0GbwC/AGQwAAAAHQZvgL8AZ"
    "DAAAAAdBmgAvwBkMAAAAB0GaIC/AGQwAAAAHQZpAL8AZDAAAAAdBmmAvwBkMAAAAB0GagC/A"
    "GQwAAAAHQZqgL8AZDAAAAAdBmsAvwBkMAAAAB0Ga4C/AGQwAAAAHQZsAL8AZDAAAAAdBmyAv"
    "wBkMAAAAB0GbQC/AGQwAAAAHQZtgL8AZDAAAAAdBm4AvwBkMAAAAB0GboC/AGQwAAAAHQZvA"
    "L8AZDAAAAAdBm+AvwBkMAAAAB0GaAC/AGQwAAAAHQZogL8AZDAAAAAdBmkAvwBkMAAAAB0Ga"
    "YC/AGQwAAAAHQZqAL8AZDAAAAAdBmqAvwBkMAAAAB0GawC/AGQwAAAAHQZrgL8AZDAAAAAdB"
    "mwAvwBkMAAAAB0GbIC/AGQwAAAAHQZtAL8AZDAAAAAdBm2AvwBkMAAAAB0GbgC/AGQwAAAAH"
    "QZugL8AZDAAAAAdBm8AvwBkMAAAAB0Gb4C/AGQwAAAAHQZoAL8AZDAAAAAdBmiAvwBkMAAAA"
    "B0GaQC/AGQwAAAAHQZpgL8AZDAAAAAdBmoAvwBkMAAAAB0GaoC/AGQwAAAAHQZrAL8AZDAAA"
    "AAdBmuAvwBkMAAAAB0GbAC/AGQwAAAAHQZsgL8AZDAAAAAdBm0AvwBkMAAAAB0GbYC/AGQwA"
    "AAAHQZuAL8AZDAAAAAdBm6AvwBkMAAAAB0GbwC/AGQwAAAAHQZvgL8AZDAAAAAdBmgAvwBkM"
    "AAAAB0GaIC/AGQwAAAAHQZpAL8AZDAAAAAdBmmAvwBkMAAAAB0GagC/AGQwAAAAHQZqgL8AZ"
    "DAAAAAdBmsAvwBkMAAAAB0Ga4C/AGQwAAAAHQZsAL8AZDAAAAAdBmyAvwBkMAAAAB0GbQC/A"
    "GQwAAAAHQZtgL8AZDAAAAAdBm4AvwBkMAAAAB0GboC/AGQwAAAAHQZvAL8AZDAAAAAdBm+Av"
    "wBkMAAAAB0GaAC/AGQwAAAAHQZogL8AZDAAAAAdBmkAvwBkMAAAAB0GaYC/AGQwAAAAHQZqA"
    "L8AZDAAAAAdBmqAvwBkMAAAAB0GawC/AGQwAAAAHQZrgL8AZDAAAAAdBmwAvwBkMAAAAB0Gb"
    "IC/AGQwAAAAHQZtAL8AZDAAAAAdBm2AvwBkMAAAAB0GbgC/AGQwAAAAHQZugL8AZDAAAAAdB"
    "m8AvwBkMAAAAB0Gb4C/AGQwAAAAHQZoAL8AZDAAAAAdBmiAvwBkMAAAAB0GaQC/AGQwAAAAH"
    "QZpgL8AZDAAAAAdBmoAvwBkMAAAAB0GaoC/AGQwAAAAHQZrAL8AZDAAAAAdBmuAvwBkMAAAA"
    "B0GbAC/AGQwAAAAHQZsgL8AZDAAAAAdBm0AvwBkMAAAAB0GbYC/AGQwAAAAHQZuAL8AZDAAA"
    "AAdBm6AvwBkMAAAAB0GbwC/AGQwAAAAHQZvgL8AZDAAAAAdBmgAvwBkMAAAAB0GaIC/AGQwA"
    "AAAHQZpAL8AZDAAAAAdBmmAvwBkMAAAAB0GagC/AGQwAAAAHQZqgL8AZDAAAAAdBmsArwBkM"
    "AAAAB0Ga4CfAGQw=";
