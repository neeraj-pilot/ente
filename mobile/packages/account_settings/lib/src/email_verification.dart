import 'package:ente_account_settings/src/account_settings_host.dart';
import 'package:ente_account_settings/src/ui/verify_password_page.dart';
import 'package:ente_frb/accounts.dart';
import 'package:flutter/material.dart';

Future<void> setEmailVerification(BuildContext context, bool enabled) async {
  final host = AccountSettings.instance.host;
  try {
    await host.setEmailMfa(enabled);
  } on AccountsError_SrpNotConfigured {
    if (!context.mounted) {
      return;
    }
    final verified = await Navigator.of(
      context,
    ).push<bool>(MaterialPageRoute(builder: (_) => const VerifyPasswordPage()));
    if (verified == true) {
      await host.setEmailMfa(enabled);
    }
  }
}
