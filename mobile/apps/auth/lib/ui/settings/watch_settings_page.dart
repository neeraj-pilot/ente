import 'dart:async';
import 'dart:io';

import 'package:ente_auth/models/code.dart';
import 'package:ente_auth/models/watch/entry.dart';
import 'package:ente_auth/models/watch/state.dart';
import 'package:ente_auth/services/watch/service.dart';
import 'package:ente_auth/store/code_store.dart';
import 'package:ente_auth/ui/settings/components/auth_settings_navigation.dart';
import 'package:ente_auth/ui/settings/components/auth_settings_page_scaffold.dart';
import 'package:ente_auth/utils/toast_util.dart';
import 'package:ente_components/ente_components.dart';
import 'package:ente_lock_screen/local_authentication_service.dart';
import 'package:ente_lock_screen/lock_screen_settings.dart';
import 'package:ente_strings/ente_strings.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class WatchSettingsPage extends StatefulWidget {
  const WatchSettingsPage({super.key});

  @override
  State<WatchSettingsPage> createState() => _WatchSettingsPageState();
}

enum _WatchAction { approve, disable, refresh }

class _WatchSettingsPageState extends State<WatchSettingsPage> {
  final service = WatchService.instance;
  _WatchAction? activeAction;
  bool get busy => activeAction != null;

  @override
  void initState() {
    super.initState();
    unawaited(service.refresh());
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: service,
    builder: (context, _) {
      final l10n = context.strings;
      final state = service.state;
      final colors = context.componentColors;
      final warningColor = Theme.of(context).brightness == Brightness.light
          ? colors.warningDarker
          : colors.warning;
      final canRefresh = switch (state.phase) {
        WatchPhase.unavailable ||
        WatchPhase.pending ||
        WatchPhase.retiring ||
        WatchPhase.failed ||
        WatchPhase.ambiguous => true,
        _ => false,
      };
      return AuthSettingsPageScaffold(
        title: l10n.watch,
        children: [
          Text(
            l10n.watchOfflineDescription,
            style: TextStyles.body.copyWith(color: colors.textLight),
          ),
          const SizedBox(height: Spacing.xl),
          MenuGroupComponent(
            items: [
              Padding(
                padding: const EdgeInsets.all(Spacing.lg),
                child: Text(switch (state.phase) {
                  WatchPhase.unavailable =>
                    Platform.isIOS
                        ? l10n.watchUnavailableApple
                        : l10n.watchUnavailableAndroid,
                  WatchPhase.setup => l10n.watchSetupInstructions,
                  WatchPhase.approval => l10n.watchApproveInstructions,
                  WatchPhase.pending => l10n.watchPending,
                  WatchPhase.synced => l10n.watchSynced,
                  WatchPhase.retiring => l10n.watchRetiring,
                  WatchPhase.disabled => l10n.watchDisabled,
                  WatchPhase.failed => l10n.watchSyncFailed,
                  WatchPhase.replacement => l10n.watchReplacement,
                  WatchPhase.ambiguous => l10n.watchAmbiguous,
                }, style: TextStyles.body.copyWith(color: colors.textBase)),
              ),
              if (state.canPublish)
                SettingsItem(
                  title: l10n.watchSelectAccounts,
                  onTap: busy
                      ? null
                      : () => pushAuthSettingsPage(
                          context,
                          _WatchAccountsPage(
                            target: state,
                            scope: service.vaultScope(),
                          ),
                        ),
                ),
            ],
          ),
          if (service.errorCode != null) ...[
            const SizedBox(height: Spacing.md),
            Text(
              _errorMessage(context, service.errorCode!),
              style: TextStyles.body.copyWith(color: warningColor),
            ),
          ],
          if (service.omittedCount > 0) ...[
            const SizedBox(height: Spacing.md),
            Text(
              l10n.watchOmittedAccounts(count: service.omittedCount),
              style: TextStyles.body.copyWith(color: colors.textLight),
            ),
          ],
          if (state.phase == WatchPhase.approval) ...[
            const SizedBox(height: Spacing.lg),
            ButtonComponent(
              label: l10n.watchApprove,
              isDisabled: busy && activeAction != _WatchAction.approve,
              shouldShowSuccessState: false,
              onTap: () => _change(_WatchAction.approve, service.approve),
            ),
          ],
          if (canRefresh) ...[
            const SizedBox(height: Spacing.lg),
            ButtonComponent(
              label: l10n.watchCheckAgain,
              variant: ButtonComponentVariant.secondary,
              isDisabled: busy && activeAction != _WatchAction.refresh,
              shouldShowSuccessState: false,
              onTap: () => _run(_WatchAction.refresh, service.refresh),
            ),
          ],
          if (state.canDisable) ...[
            const SizedBox(height: Spacing.xl),
            MenuGroupComponent(
              items: [
                MenuComponent(
                  title: l10n.watchDisable,
                  titleColor: warningColor,
                  showOnlyLoadingState: true,
                  onTap: busy && activeAction != _WatchAction.disable
                      ? null
                      : () => _change(_WatchAction.disable, service.disable),
                ),
              ],
            ),
          ],
          const SizedBox(height: Spacing.lg),
          Text(
            l10n.watchOfflineDeletionNotice,
            style: TextStyles.mini.copyWith(color: colors.textLight),
          ),
        ],
      );
    },
  );

  Future<void> _change(
    _WatchAction kind,
    Future<void> Function(WatchState, String) action,
  ) {
    final target = service.state;
    final scope = service.vaultScope();
    return _run(kind, () async {
      if (await _authenticate(context) && mounted) await action(target, scope);
    });
  }

  Future<void> _run(_WatchAction kind, Future<void> Function() action) async {
    if (busy) return;
    setState(() => activeAction = kind);
    try {
      await action();
    } on Object catch (error) {
      if (mounted) {
        showToast(context, _errorMessage(context, _errorCode(error)));
      }
    } finally {
      if (mounted) setState(() => activeAction = null);
    }
  }
}

class _WatchAccountsPage extends StatefulWidget {
  const _WatchAccountsPage({required this.target, required this.scope});
  final WatchState target;
  final String scope;

  @override
  State<_WatchAccountsPage> createState() => _WatchAccountsPageState();
}

class _WatchAccountsPageState extends State<_WatchAccountsPage> {
  final service = WatchService.instance;
  late final Future<List<Code>> codes;
  late final Set<int> selected;
  bool busy = false;

  @override
  void initState() {
    super.initState();
    selected = service.selectedIDs;
    codes = CodeStore.instance.getAllCodes();
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<List<Code>>(
    future: codes,
    builder: (context, snapshot) {
      final l10n = context.strings;
      final colors = context.componentColors;
      final supported = snapshot.data?.where(WatchEntry.supports).toList();
      final selectedCount =
          supported
              ?.where((code) => selected.contains(code.generatedID))
              .length ??
          0;
      return AuthSettingsPageScaffold(
        title: l10n.watchSelectAccounts,
        bottomNavigationBar: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.all(Spacing.lg),
            child: ButtonComponent(
              label: l10n.save,
              isDisabled: !snapshot.hasData || snapshot.hasError,
              shouldShowSuccessState: false,
              onTap: _save,
            ),
          ),
        ),
        children: [
          Text(
            l10n.watchSupportedAccounts,
            style: TextStyles.body.copyWith(color: colors.textLight),
          ),
          const SizedBox(height: Spacing.lg),
          if (snapshot.hasError)
            Text(l10n.watchSyncFailed, style: TextStyles.body)
          else if (supported == null)
            const Center(child: CircularProgressIndicator())
          else ...[
            Text(
              l10n.watchSelectedAccounts(count: selectedCount),
              style: TextStyles.mini.copyWith(color: colors.textLight),
            ),
            const SizedBox(height: Spacing.sm),
            if (supported.isEmpty)
              Text(l10n.watchNoSupportedAccounts, style: TextStyles.body)
            else
              MenuGroupComponent(
                items: [
                  for (final code in supported) _accountRow(context, code),
                ],
              ),
          ],
        ],
      );
    },
  );

  Widget _accountRow(BuildContext context, Code code) {
    final checked = selected.contains(code.generatedID);
    final colors = context.componentColors;
    void toggle() => setState(() {
      if (!selected.remove(code.generatedID)) selected.add(code.generatedID!);
    });
    return Semantics(
      checked: checked,
      enabled: !busy,
      label: [
        code.issuer,
        code.account,
      ].where((value) => value.isNotEmpty).join(', '),
      onTap: busy ? null : toggle,
      child: ExcludeSemantics(
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: busy ? null : toggle,
            child: Padding(
              padding: const EdgeInsets.all(Spacing.lg),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          code.issuer.isEmpty ? code.account : code.issuer,
                          style: TextStyles.bodyBold.copyWith(
                            color: colors.textBase,
                          ),
                        ),
                        if (code.issuer.isNotEmpty &&
                            code.account.isNotEmpty) ...[
                          const SizedBox(height: Spacing.xs),
                          Text(
                            code.account,
                            style: TextStyles.body.copyWith(
                              color: colors.textLight,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(width: Spacing.lg),
                  CheckboxComponent(
                    selected: checked,
                    onChanged: busy ? null : (_) => toggle(),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _save() async {
    if (busy) return;
    setState(() => busy = true);
    try {
      if (!await _authenticate(context) || !mounted) return;
      final current = await codes;
      selected.retainAll(
        current.where(WatchEntry.supports).map((code) => code.generatedID!),
      );
      await service.select(widget.target, widget.scope, selected);
      await service.refresh();
      if (mounted) Navigator.of(context).pop();
    } on Object catch (error) {
      if (mounted) {
        showToast(context, _errorMessage(context, _errorCode(error)));
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }
}

Future<bool> _authenticate(BuildContext context) async {
  final auth = LocalAuthenticationService.instance;
  final supported = await auth.isLocalAuthSupportedOnDevice();
  if (!context.mounted) return false;
  if (!supported && !LockScreenSettings.instance.getIsAppLockSet()) {
    showToast(context, context.strings.watchAuthenticationRequired);
    return false;
  }
  return auth.requestLocalAuthentication(
    context,
    context.strings.watchAuthenticationReason,
    useDebugAuthCache: false,
  );
}

String _errorCode(Object error) =>
    error is PlatformException ? error.code : 'watchUnavailable';

String _errorMessage(BuildContext context, String code) => switch (code) {
  'payloadTooLarge' => context.strings.watchPayloadTooLarge,
  'wrongEnrollment' || 'staleVault' => context.strings.watchSelectionChanged,
  'phoneLocked' => context.strings.watchPhoneLocked,
  _ => context.strings.watchSyncFailed,
};
