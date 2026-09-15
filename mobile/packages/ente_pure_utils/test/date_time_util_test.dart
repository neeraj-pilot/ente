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
}
