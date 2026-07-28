import 'dart:math' as math;

import 'package:ente_components/ente_components.dart';
import 'package:flutter/material.dart';

/// Auth-local settings scaffold composed from [AppBarComponent].
class AuthSettingsPageScaffold extends StatelessWidget {
  const AuthSettingsPageScaffold({
    super.key,
    required this.title,
    required this.children,
    this.subtitle,
    this.backButton,
  });

  final String title;
  final String? subtitle;
  final Widget? backButton;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final colors = context.componentColors;
    return LayoutBuilder(
      builder: (context, constraints) {
        final horizontalPadding = math.max(
          Spacing.lg,
          (constraints.maxWidth - _maxContentWidth) / 2,
        );

        return Scaffold(
          backgroundColor: colors.backgroundBase,
          body: AppBarComponent(
            title: title,
            subtitle: subtitle,
            backButton: backButton,
            horizontalPadding: horizontalPadding,
            slivers: [
              SliverSafeArea(
                top: false,
                sliver: SliverPadding(
                  padding: EdgeInsets.fromLTRB(
                    horizontalPadding,
                    0,
                    horizontalPadding,
                    Spacing.lg,
                  ),
                  sliver: SliverList.list(children: children),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

const double _maxContentWidth = 720;
