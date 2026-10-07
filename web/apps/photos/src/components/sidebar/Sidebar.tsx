import { Export } from "@/components/Export";
import { downloadAppDialogAttributes } from "@/components/utils/download";
import exportService from "@/services/export";
import { performSidebarAction as performSidebarRegistryAction } from "@/services/search/sidebar-search-registry";
import {
    Delete02Icon,
    Download05Icon,
    GeometricShapes01Icon,
    ViewOffSlashIcon,
} from "@hugeicons/core-free-icons";
import { HugeiconsIcon } from "@hugeicons/react";
import CloseIcon from "@mui/icons-material/Close";
import LockOutlinedIcon from "@mui/icons-material/LockOutlined";
import {
    Box,
    Divider,
    IconButton,
    Skeleton,
    Stack,
    styled,
} from "@mui/material";
import Typography from "@mui/material/Typography";
import { isDesktop } from "ente-base/app";
import { EnteLogo, EnteLogoBox } from "ente-base/components/EnteLogo";
import { LinkButton } from "ente-base/components/LinkButton";
import {
    RowButton,
    RowButtonEndActivityIndicator,
} from "ente-base/components/RowButton";
import { SpacedRow } from "ente-base/components/containers";
import { SidebarDrawer } from "ente-base/components/mui/SidebarDrawer";
import {
    useModalVisibility,
    type ModalVisibilityProps,
} from "ente-base/components/utils/modal";
import { useBaseContext } from "ente-base/context";
import log from "ente-base/log";
import { customAPIHost } from "ente-base/origins";
import { useUserDetailsSnapshot } from "ente-new/photos/components/utils/use-snapshot";
import {
    PseudoCollectionID,
    type CollectionSummaries,
} from "ente-new/photos/services/collection-summary";
import type { SidebarActionID } from "ente-new/photos/services/search/types";
import {
    hasExceededStorageQuota,
    isFamilyAdmin,
    isPartOfFamily,
    isSubscriptionActive,
    isSubscriptionActivePaid,
    isSubscriptionCancelled,
    isSubscriptionFree,
    isSubscriptionPastDue,
    isSubscriptionStripe,
    pullUserDetails,
    redirectToCustomerPortal,
    userDetailsAddOnBonuses,
    type UserDetails,
} from "ente-new/photos/services/user-details";
import { usePhotosAppContext } from "ente-new/photos/types/context";
import { wait } from "ente-utils/promise";
import { t } from "i18next";
import React, {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
    type MouseEventHandler,
} from "react";
import { Trans } from "react-i18next";
import { FreeUpSpace, type FreeUpSpaceAction } from "./FreeUpSpace";
import { Help, type HelpAction } from "./Help";
import { ReferralSettings } from "./ReferralSettings";
import { WatchFolder } from "./WatchFolder";
import { SubscriptionCard } from "./account/SubscriptionCard";
import { Account, type AccountAction } from "./accounts/Account";
import { ManageMemberSubscription } from "./accounts/ManageMemberSubscription";
import { openManageSubscription } from "./accounts/subscription";
import { Preferences, type PreferencesAction } from "./preferences/Preferences";

type SidebarProps = ModalVisibilityProps & {
    normalCollectionSummaries: CollectionSummaries;
    uncategorizedCollectionSummaryID: number;
    pendingAction?: SidebarActionID;
    onActionHandled?: (actionID: SidebarActionID) => void;
    onShowPlanSelector: () => void;
    onShowCollectionSummary: (
        collectionSummaryID: number,
        isHiddenCollectionSummary?: boolean,
    ) => Promise<void>;
    collectionNameByID: Map<number, string>;
    onAuthenticateUser: () => Promise<boolean>;
};

export const Sidebar: React.FC<SidebarProps> = ({
    open,
    onClose,
    normalCollectionSummaries,
    uncategorizedCollectionSummaryID,
    pendingAction,
    onActionHandled,
    onShowPlanSelector,
    onShowCollectionSummary,
    collectionNameByID,
    onAuthenticateUser,
}) => {
    const { show: showHelp, props: helpVisibilityProps } = useModalVisibility();
    const { show: showAccount, props: accountVisibilityProps } =
        useModalVisibility();
    const { show: showReferrals, props: referralsVisibilityProps } =
        useModalVisibility();
    const { show: showPreferences, props: preferencesVisibilityProps } =
        useModalVisibility();
    const { show: showFreeUpSpace, props: freeUpSpaceVisibilityProps } =
        useModalVisibility();
    const { show: showExport, props: exportVisibilityProps } =
        useModalVisibility();
    const { onClose: closeExport } = exportVisibilityProps;

    useEffect(() => {
        if (!open) closeExport();
    }, [open, closeExport]);

    const { watchFolderView, setWatchFolderView } = usePhotosAppContext();
    const { showMiniDialog, logout } = useBaseContext();

    const [pendingAccountAction, setPendingAccountAction] =
        useState<AccountAction>();
    const [pendingPreferencesAction, setPendingPreferencesAction] =
        useState<PreferencesAction>();
    const [pendingHelpAction, setPendingHelpAction] = useState<HelpAction>();
    const [pendingFreeUpSpaceAction, setPendingFreeUpSpaceAction] =
        useState<FreeUpSpaceAction>();

    const handleLogout = useCallback(
        () =>
            showMiniDialog({
                message: t("logout_message"),
                continue: {
                    text: t("logout"),
                    color: "critical",
                    action: logout,
                },
                buttonDirection: "row",
            }),
        [logout, showMiniDialog],
    );

    const handleOpenWatchFolder = useCallback(
        () => setWatchFolderView(true),
        [setWatchFolderView],
    );

    const handleCloseWatchFolder = useCallback(
        () => setWatchFolderView(false),
        [setWatchFolderView],
    );

    const handleShowExport = useCallback(() => {
        if (!isDesktop) {
            showMiniDialog(downloadAppDialogAttributes());
            return;
        }

        void (async () => {
            try {
                if (!(await onAuthenticateUser())) return;
                showExport();
            } catch (error) {
                log.error("Failed to authenticate before export", error);
            }
        })();
    }, [onAuthenticateUser, showExport, showMiniDialog]);

    const performSidebarAction = useCallback(
        async (actionID: SidebarActionID) =>
            performSidebarRegistryAction(actionID, {
                onClose,
                onShowCollectionSummary,
                onShowPlanSelector,
                showAccount,
                showReferrals,
                showPreferences,
                showHelp,
                showFreeUpSpace,
                onShowExport: handleShowExport,
                onLogout: handleLogout,
                onShowWatchFolder: handleOpenWatchFolder,
                pseudoIDs: {
                    uncategorized: uncategorizedCollectionSummaryID,
                    archive: PseudoCollectionID.archiveItems,
                    hidden: PseudoCollectionID.hiddenItems,
                    trash: PseudoCollectionID.trash,
                },
                setPendingAccountAction: (a) =>
                    setPendingAccountAction(a as AccountAction | undefined),
                setPendingPreferencesAction: (a) =>
                    setPendingPreferencesAction(
                        a as PreferencesAction | undefined,
                    ),
                setPendingHelpAction: (a) =>
                    setPendingHelpAction(a as HelpAction | undefined),
                setPendingFreeUpSpaceAction: (a) =>
                    setPendingFreeUpSpaceAction(
                        a as FreeUpSpaceAction | undefined,
                    ),
            }),
        [
            handleLogout,
            handleOpenWatchFolder,
            onClose,
            onShowCollectionSummary,
            onShowPlanSelector,
            handleShowExport,
            showAccount,
            showFreeUpSpace,
            showHelp,
            showPreferences,
            showReferrals,
            uncategorizedCollectionSummaryID,
        ],
    );

    // Closing auth changes these callback identities.
    // Letting that restart the pending action effect would reopen auth.
    const performSidebarActionRef = useRef(performSidebarAction);
    const onActionHandledRef = useRef(onActionHandled);
    useEffect(() => {
        performSidebarActionRef.current = performSidebarAction;
        onActionHandledRef.current = onActionHandled;
    });

    useEffect(() => {
        if (!pendingAction) return;
        void performSidebarActionRef
            .current(pendingAction)
            .finally(() => onActionHandledRef.current?.(pendingAction));
    }, [pendingAction]);

    return (
        <>
            <RootSidebarDrawer open={open} onClose={onClose} maxWidth="440px">
                <HeaderSection onCloseSidebar={onClose} />
                <UserDetailsSection
                    sidebarOpen={open}
                    {...{ onShowPlanSelector }}
                />
                <Stack sx={{ gap: 0.5, mb: 3 }}>
                    <ShortcutSection
                        onCloseSidebar={onClose}
                        {...{
                            normalCollectionSummaries,
                            uncategorizedCollectionSummaryID,
                            onShowCollectionSummary,
                        }}
                    />
                    <UtilitySection
                        onCloseSidebar={onClose}
                        {...{
                            showExport: handleShowExport,
                            onAuthenticateUser,
                            onShowPlanSelector,
                            showAccount,
                            accountVisibilityProps,
                            showReferrals,
                            referralsVisibilityProps,
                            showPreferences,
                            preferencesVisibilityProps,
                            showHelp,
                            helpVisibilityProps,
                            showFreeUpSpace,
                            freeUpSpaceVisibilityProps,
                            watchFolderView,
                            onShowWatchFolder: handleOpenWatchFolder,
                            onCloseWatchFolder: handleCloseWatchFolder,
                            pendingAccountAction,
                            onAccountActionHandled: setPendingAccountAction,
                            pendingPreferencesAction,
                            onPreferencesActionHandled:
                                setPendingPreferencesAction,
                            pendingHelpAction,
                            onHelpActionHandled: setPendingHelpAction,
                            pendingFreeUpSpaceAction,
                            onFreeUpSpaceActionHandled:
                                setPendingFreeUpSpaceAction,
                        }}
                    />
                    <Divider sx={{ my: "2px" }} />
                    <ExitSection onLogout={handleLogout} />
                    <InfoSection />
                </Stack>
            </RootSidebarDrawer>
            <Export
                {...exportVisibilityProps}
                open={open && exportVisibilityProps.open}
                collectionNameByID={collectionNameByID}
                onRootClose={onClose}
            />
        </>
    );
};

const RootSidebarDrawer = styled(SidebarDrawer)(({ theme }) => ({
    "& .MuiPaper-root": { padding: theme.spacing(1.5) },
}));

interface SectionProps {
    onCloseSidebar: SidebarProps["onClose"];
}

const HeaderSection: React.FC<SectionProps> = ({ onCloseSidebar }) => (
    <SpacedRow sx={{ mt: "6px", pl: "12px" }}>
        <EnteLogoBox>
            <EnteLogo height={16} />
        </EnteLogoBox>
        <IconButton
            aria-label={t("close")}
            onClick={onCloseSidebar}
            color="primary"
        >
            <CloseIcon fontSize="small" />
        </IconButton>
    </SpacedRow>
);

type UserDetailsSectionProps = Pick<SidebarProps, "onShowPlanSelector"> & {
    sidebarOpen: boolean;
};

const UserDetailsSection: React.FC<UserDetailsSectionProps> = ({
    sidebarOpen,
    onShowPlanSelector,
}) => {
    const userDetails = useUserDetailsSnapshot();
    const {
        show: showManageMemberSubscription,
        props: manageMemberSubscriptionVisibilityProps,
    } = useModalVisibility();

    useEffect(() => {
        if (sidebarOpen) void pullUserDetails();
    }, [sidebarOpen]);

    const isNonAdminFamilyMember = useMemo(
        () =>
            userDetails &&
            isPartOfFamily(userDetails) &&
            !isFamilyAdmin(userDetails),
        [userDetails],
    );

    const handleSubscriptionCardClick = () =>
        openManageSubscription({
            userDetails,
            showManageMemberSubscription,
            onShowPlanSelector,
        });

    return (
        <>
            <Box sx={{ px: 0.5, mt: 1.5, pb: 1.5, mb: 1 }}>
                <Typography sx={{ px: 1, pb: 1, color: "text.muted" }}>
                    {userDetails ? (
                        userDetails.email
                    ) : (
                        <Skeleton animation="wave" />
                    )}
                </Typography>

                <SubscriptionCard
                    userDetails={userDetails}
                    onClick={handleSubscriptionCardClick}
                />
                {userDetails && (
                    <SubscriptionStatus
                        {...{ userDetails, onShowPlanSelector }}
                    />
                )}
            </Box>
            {isNonAdminFamilyMember && userDetails && (
                <ManageMemberSubscription
                    {...manageMemberSubscriptionVisibilityProps}
                    {...{ userDetails }}
                />
            )}
        </>
    );
};

type SubscriptionStatusProps = Pick<SidebarProps, "onShowPlanSelector"> & {
    userDetails: UserDetails;
};

const SubscriptionStatus: React.FC<SubscriptionStatusProps> = ({
    userDetails,
    onShowPlanSelector,
}) => {
    const hasAMessage = useMemo(() => {
        if (isPartOfFamily(userDetails) && !isFamilyAdmin(userDetails)) {
            return false;
        }
        if (
            isSubscriptionActivePaid(userDetails.subscription) &&
            !isSubscriptionCancelled(userDetails.subscription)
        ) {
            return false;
        }
        return true;
    }, [userDetails]);

    const handleClick: MouseEventHandler<HTMLSpanElement> = useCallback(
        (e) => {
            e.stopPropagation();

            if (isSubscriptionActive(userDetails.subscription)) {
                if (hasExceededStorageQuota(userDetails)) {
                    onShowPlanSelector();
                }
            } else {
                if (
                    isSubscriptionStripe(userDetails.subscription) &&
                    isSubscriptionPastDue(userDetails.subscription)
                ) {
                    // eslint-disable-next-line @typescript-eslint/no-floating-promises
                    redirectToCustomerPortal();
                } else {
                    onShowPlanSelector();
                }
            }
        },
        [onShowPlanSelector, userDetails],
    );

    if (!hasAMessage) {
        return <></>;
    }

    const hasAddOnBonus = userDetailsAddOnBonuses(userDetails).length > 0;

    let message: React.ReactNode;
    if (!hasAddOnBonus) {
        if (isSubscriptionActive(userDetails.subscription)) {
            if (isSubscriptionFree(userDetails.subscription)) {
                message = t("subscription_info_free");
            } else if (isSubscriptionCancelled(userDetails.subscription)) {
                message = t("subscription_info_renewal_cancelled", {
                    date: userDetails.subscription.expiryTime,
                });
            }
        } else {
            message = (
                <Trans
                    i18nKey={"subscription_info_expired"}
                    components={{ a: <LinkButton onClick={handleClick} /> }}
                />
            );
        }
    }

    if (!message && hasExceededStorageQuota(userDetails)) {
        message = (
            <Trans
                i18nKey={"subscription_info_storage_quota_exceeded"}
                components={{ a: <LinkButton onClick={handleClick} /> }}
            />
        );
    }

    if (!message) return <></>;

    return (
        <Box sx={{ px: 1, pt: 0.5 }}>
            <Typography
                variant="small"
                onClick={handleClick}
                sx={{ color: "text.muted" }}
            >
                {message}
            </Typography>
        </Box>
    );
};

type ShortcutSectionProps = SectionProps &
    Pick<
        SidebarProps,
        | "normalCollectionSummaries"
        | "uncategorizedCollectionSummaryID"
        | "onShowCollectionSummary"
    >;

const ShortcutSection: React.FC<ShortcutSectionProps> = ({
    onCloseSidebar,
    normalCollectionSummaries,
    uncategorizedCollectionSummaryID,
    onShowCollectionSummary,
}) => {
    const shortcutIconSize = 20;

    const handleOpenUncategorizedSection = () =>
        void onShowCollectionSummary(uncategorizedCollectionSummaryID).then(
            onCloseSidebar,
        );

    const handleOpenTrashSection = () =>
        void onShowCollectionSummary(PseudoCollectionID.trash).then(
            onCloseSidebar,
        );

    const handleOpenArchiveSection = () =>
        void onShowCollectionSummary(PseudoCollectionID.archiveItems).then(
            onCloseSidebar,
        );

    const handleOpenHiddenSection = () =>
        void onShowCollectionSummary(PseudoCollectionID.hiddenItems, true)
            // Let focus settle before closing to avoid aria-hidden warnings.
            .then(() => wait(10))
            .then(onCloseSidebar);

    const summaryCaption = (summaryID: number) =>
        normalCollectionSummaries.get(summaryID)?.fileCount.toString();

    return (
        <>
            <RowButton
                startIcon={
                    <HugeiconsIcon
                        icon={GeometricShapes01Icon}
                        size={shortcutIconSize}
                        aria-hidden
                    />
                }
                label={t("section_uncategorized")}
                caption={summaryCaption(uncategorizedCollectionSummaryID)}
                onClick={handleOpenUncategorizedSection}
            />
            <RowButton
                startIcon={
                    <HugeiconsIcon
                        icon={Download05Icon}
                        size={shortcutIconSize}
                    />
                }
                label={t("section_archive")}
                caption={summaryCaption(PseudoCollectionID.archiveItems)}
                onClick={handleOpenArchiveSection}
            />
            <RowButton
                startIcon={
                    <HugeiconsIcon
                        icon={ViewOffSlashIcon}
                        size={shortcutIconSize}
                    />
                }
                label={t("section_hidden")}
                caption={
                    <LockOutlinedIcon
                        sx={{
                            verticalAlign: "middle",
                            fontSize: "19px !important",
                        }}
                    />
                }
                onClick={handleOpenHiddenSection}
            />
            <RowButton
                startIcon={
                    <HugeiconsIcon
                        icon={Delete02Icon}
                        size={shortcutIconSize}
                    />
                }
                label={t("section_trash")}
                caption={summaryCaption(PseudoCollectionID.trash)}
                onClick={handleOpenTrashSection}
            />
        </>
    );
};

type UtilitySectionProps = SectionProps &
    Pick<SidebarProps, "onAuthenticateUser" | "onShowPlanSelector"> & {
        showExport: () => void;
        showAccount: () => void;
        accountVisibilityProps: ModalVisibilityProps;
        showReferrals: () => void;
        referralsVisibilityProps: ModalVisibilityProps;
        showPreferences: () => void;
        preferencesVisibilityProps: ModalVisibilityProps;
        showHelp: () => void;
        helpVisibilityProps: ModalVisibilityProps;
        showFreeUpSpace: () => void;
        freeUpSpaceVisibilityProps: ModalVisibilityProps;
        watchFolderView: boolean;
        onShowWatchFolder: () => void;
        onCloseWatchFolder: () => void;
        pendingAccountAction?: AccountAction;
        onAccountActionHandled: (action?: AccountAction) => void;
        pendingPreferencesAction?: PreferencesAction;
        onPreferencesActionHandled: (action?: PreferencesAction) => void;
        pendingHelpAction?: HelpAction;
        onHelpActionHandled: (action?: HelpAction) => void;
        pendingFreeUpSpaceAction?: FreeUpSpaceAction;
        onFreeUpSpaceActionHandled: (action?: FreeUpSpaceAction) => void;
    };

const UtilitySection: React.FC<UtilitySectionProps> = ({
    onCloseSidebar,
    showExport,
    onAuthenticateUser,
    onShowPlanSelector,
    showAccount,
    accountVisibilityProps,
    showReferrals,
    referralsVisibilityProps,
    showPreferences,
    preferencesVisibilityProps,
    showHelp,
    helpVisibilityProps,
    showFreeUpSpace,
    freeUpSpaceVisibilityProps,
    watchFolderView,
    onShowWatchFolder,
    onCloseWatchFolder,
    pendingAccountAction,
    onAccountActionHandled,
    pendingPreferencesAction,
    onPreferencesActionHandled,
    pendingHelpAction,
    onHelpActionHandled,
    pendingFreeUpSpaceAction,
    onFreeUpSpaceActionHandled,
}) => {
    return (
        <>
            <RowButton
                variant="secondary"
                label={t("account")}
                onClick={showAccount}
            />
            <RowButton
                variant="secondary"
                label={t("referrals")}
                onClick={showReferrals}
            />
            {isDesktop && (
                <RowButton
                    variant="secondary"
                    label={t("watch_folders")}
                    onClick={onShowWatchFolder}
                />
            )}
            <RowButton
                variant="secondary"
                label={t("free_up_space")}
                onClick={showFreeUpSpace}
            />
            <RowButton
                variant="secondary"
                label={t("preferences")}
                onClick={showPreferences}
            />
            <RowButton
                variant="secondary"
                label={t("help")}
                onClick={showHelp}
            />
            <RowButton
                variant="secondary"
                label={t("export_data")}
                endIcon={
                    exportService.isExportInProgress() && (
                        <RowButtonEndActivityIndicator />
                    )
                }
                onClick={showExport}
            />
            <Help
                {...helpVisibilityProps}
                onRootClose={onCloseSidebar}
                pendingAction={pendingHelpAction}
                onActionHandled={onHelpActionHandled}
            />
            {isDesktop && (
                <WatchFolder
                    open={watchFolderView}
                    onClose={onCloseWatchFolder}
                    onRootClose={onCloseSidebar}
                />
            )}
            <Account
                {...accountVisibilityProps}
                onRootClose={onCloseSidebar}
                pendingAction={pendingAccountAction}
                onActionHandled={onAccountActionHandled}
                {...{ onAuthenticateUser, onShowPlanSelector }}
            />
            <ReferralSettings
                {...referralsVisibilityProps}
                onRootClose={onCloseSidebar}
            />
            <Preferences
                {...preferencesVisibilityProps}
                onRootClose={onCloseSidebar}
                pendingAction={pendingPreferencesAction}
                onActionHandled={onPreferencesActionHandled}
                onAuthenticateUser={onAuthenticateUser}
            />
            <FreeUpSpace
                {...freeUpSpaceVisibilityProps}
                onRootClose={onCloseSidebar}
                pendingAction={pendingFreeUpSpaceAction}
                onActionHandled={onFreeUpSpaceActionHandled}
            />
        </>
    );
};

const ExitSection: React.FC<{ onLogout: () => void }> = ({ onLogout }) => (
    <>
        <RowButton
            variant="secondary"
            color="critical"
            label={t("logout")}
            onClick={onLogout}
        />
    </>
);

const InfoSection: React.FC = () => {
    const [appVersion, setAppVersion] = useState("");
    const [host, setHost] = useState<string | undefined>("");

    useEffect(() => {
        void globalThis.electron?.appVersion().then(setAppVersion);
        void customAPIHost().then(setHost);
    }, []);

    return (
        <>
            <Stack
                sx={{
                    p: "24px 18px 16px 18px",
                    gap: "24px",
                    color: "text.muted",
                }}
            >
                {appVersion && (
                    <Typography variant="mini">{appVersion}</Typography>
                )}
                {host && <Typography variant="mini">{host}</Typography>}
            </Stack>
        </>
    );
};
