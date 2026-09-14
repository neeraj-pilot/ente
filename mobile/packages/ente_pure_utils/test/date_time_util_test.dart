import 'package:ente_pure_utils/ente_pure_utils.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ISO week boundaries', () {
    test('use Monday midnight regardless of the original time', () {
      final mondayMorning = DateTime(2026, 8, 17, 9);
      final sundayEvening = DateTime(2026, 8, 23, 18);

      expect(startOfISOWeek(mondayMorning), DateTime(2026, 8, 17));
      expect(startOfISOWeek(sundayEvening), DateTime(2026, 8, 17));
      expect(startOfNextISOWeek(mondayMorning), DateTime(2026, 8, 24));
      expect(startOfNextISOWeek(sundayEvening), DateTime(2026, 8, 24));
    });

    test('normalize weeks across year boundaries', () {
      final sunday = DateTime(2026, 1, 4, 23, 59, 59, 999, 999);

      expect(startOfISOWeek(sunday), DateTime(2025, 12, 29));
      expect(startOfNextISOWeek(sunday), DateTime(2026, 1, 5));
    });

    test('preserve UTC dates', () {
      final sunday = DateTime.utc(2026, 8, 23, 18);
      final start = startOfISOWeek(sunday);
      final nextStart = startOfNextISOWeek(sunday);

      expect(start, DateTime.utc(2026, 8, 17));
      expect(nextStart, DateTime.utc(2026, 8, 24));
      expect(start.isUtc, isTrue);
      expect(nextStart.isUtc, isTrue);
    });
  });

  group("filename dates", () {
    final cases = {
      "IMG-20221109-WA0000": DateTime(2022, 11, 9),
      "Screenshot_20220807-195908_Firefox": DateTime(2022, 8, 7, 19, 59, 8),
      "Screenshot_20220507-195908": DateTime(2022, 5, 7, 19, 59, 8),
      "2022-02-18 16.00.12-DCMX.png": DateTime(2022, 2, 18, 16, 0, 12),
      "20221107_231730": DateTime(2022, 11, 7, 23, 17, 30),
      "2020-11-01 02.31.02": DateTime(2020, 11, 1, 2, 31, 2),
      "IMG_20210921_144423": DateTime(2021, 9, 21, 14, 44, 23),
      "2019-10-31 155703": DateTime(2019, 10, 31, 15, 57, 3),
      "IMG_20210921_144423_783": DateTime(2021, 9, 21, 14, 44, 23),
      "Screenshot_2022-06-21-16-51-29-164_newFormat.heic": DateTime(
        2022,
        6,
        21,
        16,
        51,
        29,
      ),
      "Screenshot 20221106 211633.com.google.android.apps.nbu.paisa.user.jpg":
          DateTime(2022, 11, 6, 21, 16, 33),
      "signal-2022-12-17-15-16-04-718.jpg": DateTime(2022, 12, 17, 15, 16, 4),
      "signal-2022-12-17-15-16-04-718-2.jpg": DateTime(2022, 12, 17, 15, 16, 4),
      "signal-2026-08-31-195517.jpeg": DateTime(2026, 8, 31, 19, 55, 17),
      "signal-2026-08-31-195517-1.jpeg": DateTime(2026, 8, 31, 19, 55, 17),
      "signal-2026-08-31-195517.mp4": DateTime(2026, 8, 31, 19, 55, 17),
      "signal-2026-08-31-195517-12.mp4": DateTime(2026, 8, 31, 19, 55, 17),
      "PHOTO-2026-07-02-15-15-31.jpg": DateTime(2026, 7, 2, 15, 15, 31),
      "PHOTO-2026-07-02-15-15-31-1.jpg": DateTime(2026, 7, 2, 15, 15, 31),
      "2024-02-29 23:59:59.jpg": DateTime(2024, 2, 29, 23, 59, 59),
      "2000-02-29.jpg": DateTime(2000, 2, 29),
      "2024-01-01.jpg": DateTime(2024, 1, 1),
      "20240101.mp4": DateTime(2024, 1, 1),
      "IMG-20240101-WA0001.jpg": DateTime(2024, 1, 1),
      "IMG_2024.08.31_195517.jpg": DateTime(2024, 8, 31, 19, 55, 17),
      "2024.02.29.jpg": DateTime(2024, 2, 29),
      "2024-01-01-edited.jpg": DateTime(2024, 1, 1),
      "IMG_20240101_HDR.jpg": DateTime(2024, 1, 1),
      "IMG_20240101_HDR": DateTime(2024, 1, 1),
      "2024-01-01 12:34:56,789.jpg": DateTime(2024, 1, 1, 12, 34, 56, 789),
      "2024-01-01 12:34:56.1.jpg": DateTime(2024, 1, 1, 12, 34, 56, 100),
      "2024-01-01 12:34:56.123456.jpg": DateTime(
        2024,
        1,
        1,
        12,
        34,
        56,
        123,
        456,
      ),
      "2024-01-01 12:34:56+02:00.jpg": DateTime.utc(2024, 1, 1, 10, 34, 56),
      "2024-01-01 12:34:56+0200.jpg": DateTime.utc(2024, 1, 1, 10, 34, 56),
      "2024-01-01 12:34:56-03:30.jpg": DateTime.utc(2024, 1, 1, 16, 4, 56),
      "2024-01-01 12:34:56-0330.jpg": DateTime.utc(2024, 1, 1, 16, 4, 56),
      "20240101T123456-0330.jpg": DateTime.utc(2024, 1, 1, 16, 4, 56),
      "2024-01-01T12:34:56-0330.jpg": DateTime.utc(2024, 1, 1, 16, 4, 56),
      "2024-01-01 12:34:56-0330-edited.jpg": DateTime.utc(
        2024,
        1,
        1,
        16,
        4,
        56,
      ),
      "2024-01-01 00:15:00+01:00.jpg": DateTime.utc(2023, 12, 31, 23, 15),
      "2024-01-01 23:45:00-00:30.jpg": DateTime.utc(2024, 1, 2, 0, 15),
      "2024-01-01 23:45:00-0030.jpg": DateTime.utc(2024, 1, 2, 0, 15),
      "20240101T123456Z.jpg": DateTime.utc(2024, 1, 1, 12, 34, 56),
      "20240101T123456z.jpg": DateTime.utc(2024, 1, 1, 12, 34, 56),
      "2024-01-01 12:34:56.123456+02:00.jpg": DateTime.utc(
        2024,
        1,
        1,
        10,
        34,
        56,
        123,
        456,
      ),
      "2024-01-01 12:34:56.123456-0330.jpg": DateTime.utc(
        2024,
        1,
        1,
        16,
        4,
        56,
        123,
        456,
      ),
      "signal-2026-08-31-195517-0100.jpeg": DateTime(2026, 8, 31, 19, 55, 17),
      "signal-2026-08-31-195517-0330.jpeg": DateTime(2026, 8, 31, 19, 55, 17),
      "signal-2026-08-31-195517-2460.jpeg": DateTime(2026, 8, 31, 19, 55, 17),
      "PHOTO-2026-07-02-15-15-31-0330.jpg": DateTime(2026, 7, 2, 15, 15, 31),
    };
    for (final entry in cases.entries) {
      test("parses ${entry.key}", () {
        expect(parseDateTimeFromFileNameV2(entry.key), entry.value);
      });
    }

    for (final name in [
      "",
      "photo.jpg",
      "IMG_0123.JPG",
      "Snapchat-431959199.mp4.",
      "Snapchat-400000000.mp4",
      "Snapchat-900000000.mp4",
      "2023-02-29.jpg",
      "2023.02.29.jpg",
      "IMG_2024.08-31_195517.jpg",
      "IMG_2024.0831_195517.jpg",
      "2024-02-30.jpg",
      "2024-00-01.jpg",
      "2024-13-01.jpg",
      "2024-01-00.jpg",
      "2024-04-31.jpg",
      "2024-01-01-240000.jpg",
      "2024-01-01-126000.jpg",
      "2024-01-01-120060.jpg",
      "2024-01-01-12-34.jpg",
      "2024-01-01-12-34:56.jpg",
      "2024-01-01-1234567.jpg",
      "2024-0101-123456.jpg",
      "20240101-1.jpg",
      "120240101-123456.jpg",
      "2024a0101-123456.jpg",
      "IMG_20240101_1234.jpg",
      "IMG_20240101_HDR_1234.jpg",
      "2024-01-01-edited1234.jpg",
      "2024-01-01 12:34.jpg",
      "2024-01-01  12:34:56.jpg",
      "2024-01-01t12:34:56.jpg",
      "2024-01-01.123456.jpg",
      "20240101abcdef987654.jpg",
      "2024-01-01 12:34:56+24:00.jpg",
      "2024-01-01 12:34:56+02:60.jpg",
      "2024-01-01 12:34:56-24:00.jpg",
      "2024-01-01 12:34:56-02:60.jpg",
      "2024-01-01 12:34:56-2400.jpg",
      "2024-01-01 12:34:56-0260.jpg",
      "2024-01-01 12:34:56-03.jpg",
      "2024-01-01 12:34:56-033.jpg",
      "2024-01-01 12:34:56-03300.jpg",
      "2024-01-01 12:34:56+02:000.jpg",
      "2024-01-01 12:34:56+02.jpg",
      "2024-01-01 12:34:56,7890123.jpg",
      "2024-01-01 12:34:56.1234567.jpg",
      "2024-01-01 12:34:56,garbage.jpg",
      "2024-01-01 12:34:56 extra.jpg",
      "2024-01-01 12:34:56Zgarbage.jpg",
    ]) {
      test("rejects $name", () {
        expect(parseDateTimeFromFileNameV2(name), isNull);
      });
    }

    test("respects year bounds", () {
      expect(parseDateTimeFromFileNameV2("19891231.jpg"), isNull);
      expect(
        parseDateTimeFromFileNameV2("19891231.jpg", minYear: 1980),
        DateTime(1989, 12, 31),
      );
      expect(
        parseDateTimeFromFileNameV2("1900-02-29.jpg", minYear: 1900),
        isNull,
      );
      expect(
        parseDateTimeFromFileNameV2("20260831.jpg", maxYear: 2025),
        isNull,
      );
      expect(
        parseDateTimeFromFileNameV2("20260831.jpg", maxYear: 2026),
        DateTime(2026, 8, 31),
      );
      final nextYear = currentYear + 1;
      expect(
        parseDateTimeFromFileNameV2("$nextYear-01-01.jpg"),
        DateTime(nextYear, 1, 1),
      );
      expect(parseDateTimeFromFileNameV2("${nextYear + 1}-01-01.jpg"), isNull);
    });
  });
}
