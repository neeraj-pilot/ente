import "package:photos/models/ffmpeg/ffprobe_keys.dart";
import "package:photos/models/ffmpeg/ffprobe_props.dart";
import "package:test/test.dart";

void main() {
  group("FFProbeProps", () {
    test("keeps zero-denominator frame rates unformatted", () {
      final props = FFProbeProps.parseData({
        "streams": [
          {FFProbeKeys.rFrameRate: "0/0"},
        ],
      });

      expect(props.fps, "0/0");
      expect(props.propData![FFProbeKeys.rFrameRate], "0/0");
    });

    test("formats valid frame rates", () {
      final props = FFProbeProps.parseData({
        "streams": [
          {FFProbeKeys.rFrameRate: "30000/1001"},
        ],
      });

      expect(props.fps, "29.97");
    });
  });
}
