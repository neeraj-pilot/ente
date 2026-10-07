import { Box, Dialog, DialogContent, Stack } from "@mui/material";
import Typography from "@mui/material/Typography";
import { SpacedRow } from "ente-base/components/containers";
import { DialogCloseIconButton } from "ente-base/components/mui/DialogCloseIconButton";
import { FocusVisibleButton } from "ente-base/components/mui/FocusVisibleButton";
import { useIsSmallWidth } from "ente-base/components/utils/hooks";
import type { ModalVisibilityProps } from "ente-base/components/utils/modal";
import { useBaseContext } from "ente-base/context";
import {
    familyAdminEmail,
    leaveFamily,
    type UserDetails,
} from "ente-new/photos/services/user-details";
import { t } from "i18next";
import React from "react";

type ManageMemberSubscriptionProps = ModalVisibilityProps & {
    userDetails: UserDetails;
};

export const ManageMemberSubscription: React.FC<
    ManageMemberSubscriptionProps
> = ({ open, onClose, userDetails }) => {
    const { showMiniDialog } = useBaseContext();
    const fullScreen = useIsSmallWidth();

    const confirmLeaveFamily = () =>
        showMiniDialog({
            title: t("leave_family_plan"),
            message: t("leave_family_plan_confirm"),
            continue: {
                text: t("leave"),
                color: "critical",
                action: leaveFamily,
            },
        });

    return (
        <Dialog {...{ open, onClose, fullScreen }} maxWidth="xs" fullWidth>
            <SpacedRow sx={{ p: "20px 8px 12px 16px" }}>
                <Stack>
                    <Typography variant="h3">{t("subscription")}</Typography>
                    <Typography sx={{ color: "text.muted" }}>
                        {t("family_plan")}
                    </Typography>
                </Stack>
                <DialogCloseIconButton {...{ onClose }} />
            </SpacedRow>
            <DialogContent>
                <Stack sx={{ alignItems: "center", mx: 2 }}>
                    <Box sx={{ mb: 4 }}>
                        <Typography sx={{ color: "text.muted" }}>
                            {t("subscription_info_family")}
                        </Typography>
                        <Typography>
                            {familyAdminEmail(userDetails) ?? ""}
                        </Typography>
                    </Box>
                    <img
                        height={256}
                        src="/images/family-plan/1x.png"
                        srcSet="/images/family-plan/2x.png 2x, /images/family-plan/3x.png 3x"
                    />
                    <FocusVisibleButton
                        fullWidth
                        variant="outlined"
                        color="critical"
                        onClick={confirmLeaveFamily}
                    >
                        {t("leave_family_plan")}
                    </FocusVisibleButton>
                </Stack>
            </DialogContent>
        </Dialog>
    );
};
