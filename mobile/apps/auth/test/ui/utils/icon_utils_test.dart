import 'dart:convert';
import 'dart:io';

import 'package:ente_auth/ente_theme_data.dart';
import 'package:ente_auth/ui/utils/icon_utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('simple icon registry entries resolve to bundled SVG assets', () {
    final missingAssets = <String>[];
    final registry =
        json.decode(
              File(
                'assets/simple-icons/_data/simple-icons.json',
              ).readAsStringSync(),
            )
            as List<dynamic>;

    for (final icon in registry.cast<Map<String, dynamic>>()) {
      final title = icon['title'].toString().replaceAll(' ', '').toLowerCase();
      final slug = icon['slug']?.toString();
      final assetPath =
          'assets/simple-icons/icons/${simpleIconAssetStem(title, slug)}.svg';
      final asset = File(assetPath);
      if (!asset.existsSync()) {
        missingAssets.add(assetPath);
      } else if (asset.lengthSync() == 0) {
        missingAssets.add('$assetPath is empty');
      }
    }

    expect(missingAssets, isEmpty);
  });

  test('custom icon registry entries resolve to bundled SVG assets', () {
    final missingAssets = <String>[];
    final registry =
        json.decode(
              File(
                'assets/custom-icons/_data/custom-icons.json',
              ).readAsStringSync(),
            )
            as Map<String, dynamic>;

    for (final icon in (registry['icons'] as List<dynamic>).cast<Map>()) {
      final title = icon['title'].toString();
      final titleKey = title.replaceAll(' ', '').toLowerCase();
      final slug = icon['slug']?.toString();
      final canonicalPath = 'assets/custom-icons/icons/${slug ?? titleKey}.svg';
      _expectAssetExists(canonicalPath, missingAssets);
    }

    expect(missingAssets, isEmpty);
  });

  testWidgets('black icons use the theme icon color in dark mode', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: darkThemeData,
        home: Builder(
          builder: (context) => IconUtils.instance.getSVGIcon(
            'assets/simple-icons/icons/apple.svg',
            'Apple',
            '000000',
            24,
            context,
          ),
        ),
      ),
    );

    final icon = tester.widget<SvgPicture>(find.byType(SvgPicture));
    expect(
      icon.colorFilter,
      ColorFilter.mode(darkThemeData.colorScheme.iconColor, BlendMode.srcIn),
    );
  });
}

void _expectAssetExists(String assetPath, List<String> missingAssets) {
  final asset = File(assetPath);
  if (!asset.existsSync()) {
    missingAssets.add(assetPath);
  } else if (asset.lengthSync() == 0) {
    missingAssets.add('$assetPath is empty');
  }
}
