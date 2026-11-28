import "package:flutter/material.dart";
import "package:flutter_test/flutter_test.dart";
import "package:photos/services/machine_learning/ocr/ocr_models.dart";
import "package:photos/ui/viewer/file/ocr/text_overlay_widget.dart";

void main() {
  Future<Offset> pumpOverlay(
    WidgetTester tester,
    TextOverlayController controller,
    VoidCallback onLongPressSelection,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: SizedBox.square(
            dimension: 400,
            child: TextOverlayWidget(
              imageSize: const Size.square(400),
              textBlocks: const [
                TextBlock(
                  text: "hello world",
                  confidence: 1,
                  points: [
                    Offset(100, 190),
                    Offset(300, 190),
                    Offset(300, 210),
                    Offset(100, 210),
                  ],
                  characters: [],
                ),
              ],
              controller: controller,
              isImageZoomed: false,
              uiScale: 1,
              uiOffset: Offset.zero,
              onLongPressSelection: onLongPressSelection,
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    return tester.getCenter(find.byType(TextOverlayWidget));
  }

  testWidgets("reports selections made by a long press", (tester) async {
    final controller = TextOverlayController();
    var reports = 0;
    final center = await pumpOverlay(tester, controller, () => reports++);

    controller.selectTextAtPosition(center - const Offset(60, 0));
    await tester.pump();
    expect(controller.hasActiveSelection, isTrue);
    expect(reports, 1);

    controller.selectTextAtPosition(center + const Offset(60, 0));
    await tester.pump();
    expect(reports, 2);
  });

  testWidgets("does not report selections made by a drag", (tester) async {
    final controller = TextOverlayController();
    var reports = 0;
    final center = await pumpOverlay(tester, controller, () => reports++);

    await tester.dragFrom(center - const Offset(80, 0), const Offset(60, 0));
    await tester.pump();
    expect(controller.hasActiveSelection, isTrue);
    expect(reports, 0);
  });
}
