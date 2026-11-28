import "package:flutter/material.dart";
import "package:flutter_test/flutter_test.dart";
import "package:photos/services/machine_learning/ocr/ocr_models.dart";
import "package:photos/ui/viewer/file/ocr/text_overlay_widget.dart";

void main() {
  testWidgets("reports only the start of each selection", (tester) async {
    final controller = TextOverlayController();
    var selectionStarts = 0;
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
              onSelectionStarted: () => selectionStarts++,
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    final center = tester.getCenter(find.byType(TextOverlayWidget));
    final hello = center - const Offset(60, 0);
    final world = center + const Offset(60, 0);

    controller.selectTextAtPosition(hello);
    await tester.pump();
    expect(controller.hasActiveSelection, isTrue);
    expect(selectionStarts, 1);

    controller.selectTextAtPosition(world);
    await tester.pump();
    expect(selectionStarts, 1);

    controller.clearSelection();
    await tester.pump();
    controller.selectTextAtPosition(world);
    await tester.pump();
    expect(selectionStarts, 2);
  });
}
