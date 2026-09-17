import 'dart:convert';

import 'package:ente_auth/models/code.dart';
import 'package:ente_auth/models/code_display.dart';
import 'package:ente_auth/ui/settings/data/import/import_file_cleanup.dart';
import 'package:ente_auth/ui/settings/data/import/import_flow.dart';
import 'package:ente_strings/ente_strings.dart';
import 'package:flutter/material.dart';
import 'package:logging/logging.dart';

Future<void> showTwoStableImportInstruction(BuildContext context) async {
  final l10n = context.strings;
  await showFileImportInstruction(
    context: context,
    title: 'Authenticator App (2Stable)',
    body: l10n.importAuthenticatorAppGuide,
    actionLabel: l10n.importSelectAppExport(appName: 'Authenticator App'),
    semanticsIdentifier: 'auth_import_instruction_two_stable',
    onImport: () => _pickTwoStableJsonFile(context),
  );
}

Future<void> _pickTwoStableJsonFile(BuildContext context) async {
  await pickAndProcessImportFile(
    context: context,
    dialogTitle: context.strings.importSelectJsonFile,
    logger: Logger('TwoStableImport'),
    logMessage: 'Failed to import 2Stable export',
    process: (path, _) => _processTwoStableExportFile(path),
  );
}

Future<int> _processTwoStableExportFile(String path) async {
  final jsonString = await readPickedImportFileAsString(path);
  return saveImportedCodes(parseTwoStableCodes(jsonDecode(jsonString)));
}

List<Code> parseTwoStableCodes(Object? data) {
  if (data is! Map || data['items'] is! List) {
    throw const FormatException('Invalid 2Stable export');
  }

  return (data['items'] as List)
      .map((entry) {
        if (entry is! Map) {
          throw ImportEntryParseException(
            entry: entry,
            error: const FormatException('Invalid 2Stable entry'),
          );
        }

        return parseImportOtpCode(entry, () {
          final account = entry['account'];
          final issuer = entry['issuer'];
          final secret = entry['secret'];
          final notes = entry['notes'];
          if (account is! String ||
              issuer is! String ||
              secret is! String ||
              secret.isEmpty ||
              (notes != null && notes is! String)) {
            throw const FormatException('Invalid 2Stable entry');
          }

          return buildImportOtpUri(
            kind: 'totp',
            issuer: Uri.encodeComponent(issuer),
            account: Uri.encodeComponent(account),
            secret: secret,
            algorithm: 'SHA1',
            digits: Code.defaultDigits,
            period: entry['period'],
          );
        }).copyWith(display: CodeDisplay(note: entry['notes'] ?? ''));
      })
      .toList(growable: false);
}
