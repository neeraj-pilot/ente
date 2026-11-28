import 'package:ente_account_settings/src/account_settings_host.dart';
import 'package:ente_components/ente_components.dart';
import 'package:ente_frb/accounts.dart';
import 'package:ente_strings/ente_strings.dart';
import 'package:flutter/material.dart';
import 'package:logging/logging.dart';

class VerifyPasswordPage extends StatefulWidget {
  const VerifyPasswordPage({super.key});

  @override
  State<VerifyPasswordPage> createState() => _VerifyPasswordPageState();
}

class _VerifyPasswordPageState extends State<VerifyPasswordPage> {
  final _logger = Logger('VerifyPasswordPage');
  final _passwordController = TextEditingController();
  bool _hasPassword = false;
  String? _error;
  Future<void>? _verification;

  AccountSettingsHost get _host => AccountSettings.instance.host;

  @override
  void dispose() {
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: context.componentColors.backgroundBase,
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.fromLTRB(
          Spacing.lg,
          0,
          Spacing.lg,
          Spacing.xl,
        ),
        child: ButtonComponent(
          label: context.strings.verifyPassword,
          isDisabled: !_hasPassword,
          shouldSurfaceExecutionStates: true,
          shouldShowSuccessState: false,
          onTap: _verify,
        ),
      ),
      body: AppBarComponent(
        title: context.strings.enterPassword,
        subtitle: _host.getEmail(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: Spacing.lg),
            sliver: SliverToBoxAdapter(
              child: TextInputComponent(
                controller: _passwordController,
                hintText: context.strings.enterYourPasswordHint,
                autofocus: true,
                isPasswordInput: true,
                autofillHints: const [AutofillHints.password],
                textInputAction: TextInputAction.done,
                onSubmit: _hasPassword ? (_) => _verify() : null,
                message: _error,
                messageType: TextInputComponentMessageType.error,
                onChanged: (value) => setState(() {
                  _hasPassword = value.isNotEmpty;
                  _error = null;
                }),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _verify() => _verification ??= _runVerification().whenComplete(
    () => _verification = null,
  );

  Future<void> _runVerification() async {
    try {
      await _host.setupSrp(_passwordController.text);
    } on AccountsError_IncorrectPassword {
      if (mounted) {
        setState(() => _error = context.strings.incorrectPasswordTitle);
      }
      return;
    } catch (e, s) {
      _logger.severe('Failed to set up SRP', e, s);
      if (mounted) {
        await showErrorBottomSheetComponent<void>(
          context: context,
          title: context.strings.error,
          message: context.strings.somethingWentWrong,
        );
      }
      return;
    }
    if (mounted) {
      Navigator.of(context).pop(true);
    }
  }
}
