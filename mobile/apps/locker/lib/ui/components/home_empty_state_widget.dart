import "package:dotted_border/dotted_border.dart";
import "package:ente_components/ente_components.dart";
import "package:flutter/material.dart";
import 'package:locker/l10n/l10n.dart';

class HomeEmptyStateWidget extends StatelessWidget {
  const HomeEmptyStateWidget({this.isLoading = false, super.key});

  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    final colors = context.componentColors;
    return isLoading
        ? CircularProgressIndicator(strokeWidth: 3, color: colors.primary)
        : DottedBorder(
            options: RoundedRectDottedBorderOptions(
              strokeWidth: 1,
              color: colors.textLighter,
              dashPattern: const [5, 5],
              radius: const Radius.circular(24),
            ),
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                color: colors.fillLight,
                borderRadius: BorderRadius.circular(24),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 42),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Image.asset('assets/upload_file.png'),
                  const SizedBox(height: 12),
                  Text(context.l10n.homeLockerEmptyTitle, style: TextStyles.h2),
                  const SizedBox(height: 8),
                  Text(
                    context.l10n.homeLockerEmptySubtitle,
                    style: TextStyles.body.copyWith(
                      color: colors.primary,
                      decoration: TextDecoration.none,
                    ),
                  ),
                ],
              ),
            ),
          );
  }
}
