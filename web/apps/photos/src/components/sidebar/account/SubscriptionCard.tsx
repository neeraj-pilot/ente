import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import CircleIcon from "@mui/icons-material/Circle";
import {
    Box,
    Skeleton,
    Stack,
    Typography,
    styled,
    useMediaQuery,
} from "@mui/material";
import { Overlay } from "ente-base/components/containers";
import type { ButtonishProps } from "ente-base/components/mui";
import { bytesInGB, formattedStorageByteSize } from "ente-gallery/utils/units";
import { UnstyledButton } from "ente-new/photos/components/UnstyledButton";
import type { UserDetails } from "ente-new/photos/services/user-details";
import {
    familyMemberStorageLimit,
    familyUsage,
    isPartOfFamilyWithOtherMembers,
} from "ente-new/photos/services/user-details";
import { t } from "i18next";
import type React from "react";

interface SubscriptionCardProps {
    userDetails: UserDetails | undefined;
    onClick: () => void;
}

export const SubscriptionCard: React.FC<SubscriptionCardProps> = ({
    userDetails,
    onClick,
}) =>
    !userDetails ? (
        <Skeleton
            animation="wave"
            variant="rectangular"
            height={152}
            sx={{ borderRadius: "8px" }}
        />
    ) : (
        <Box sx={{ position: "relative", color: "white" }}>
            <BackgroundOverlay />
            <SubscriptionCardContentOverlay userDetails={userDetails} />
            <ClickOverlay onClick={onClick} />
        </Box>
    );

const BackgroundOverlay: React.FC = () => (
    <img
        style={{ aspectRatio: "2/1", verticalAlign: "bottom" }}
        width="100%"
        src="/images/subscription-card-background/1x.png"
        srcSet="/images/subscription-card-background/2x.png 2x, /images/subscription-card-background/3x.png 3x"
    />
);

const ClickOverlay: React.FC<ButtonishProps> = ({ onClick }) => (
    <ClickOverlayButton onClick={onClick}>
        <ChevronRightIcon />
    </ClickOverlayButton>
);

const ClickOverlayButton = styled(UnstyledButton)(
    ({ theme }) => `
    position: absolute;
    width: 100%;
    height: 100%;
    top: 0;
    left: 0;

    display: flex;
    justify-content: flex-end;
    align-items: center;

    color: inherit;

    &:focus-visible {
        outline: 1.5px solid ${theme.vars.palette.stroke.base};
        outline-offset: 2px;
        border-radius: 3px;
    }
    &:active {
        outline: 2px solid ${theme.vars.palette.stroke.faint};
        outline-offset: 1px;
        border-radius: 3px;
    }
`,
);

interface SubscriptionCardContentOverlayProps {
    userDetails: UserDetails;
}

const SubscriptionCardContentOverlay: React.FC<
    SubscriptionCardContentOverlayProps
> = ({ userDetails }) => {
    const inFamily = isPartOfFamilyWithOtherMembers(userDetails);
    const storageLimit = inFamily
        ? familyMemberStorageLimit(userDetails)
        : undefined;

    return (
        <Overlay>
            <Stack
                sx={{
                    height: "100%",
                    justifyContent: "space-between",
                    padding: "20px 16px",
                }}
            >
                {inFamily ? (
                    storageLimit !== undefined ? (
                        <UserSubscriptionCardContents
                            userDetails={userDetails}
                            totalStorage={storageLimit}
                        />
                    ) : (
                        <FamilySubscriptionCardContents
                            userDetails={userDetails}
                        />
                    )
                ) : (
                    <UserSubscriptionCardContents
                        userDetails={userDetails}
                        totalStorage={
                            userDetails.subscription.storage +
                            userDetails.storageBonus
                        }
                    />
                )}
            </Stack>
        </Overlay>
    );
};

type UserSubscriptionCardContentsProps = SubscriptionCardContentOverlayProps & {
    totalStorage: number;
};

const UserSubscriptionCardContents: React.FC<
    UserSubscriptionCardContentsProps
> = ({ userDetails, totalStorage }) => (
    <>
        <StorageSection storage={totalStorage} usage={userDetails.usage} />
        <IndividualUsageSection
            usage={userDetails.usage}
            fileCount={userDetails.fileCount}
            storage={totalStorage}
        />
    </>
);

interface StorageSectionProps {
    usage: number;
    storage: number;
}

const StorageSection: React.FC<StorageSectionProps> = ({ usage, storage }) => {
    const isExtraSmallWidth = useMediaQuery("(width < 360px)");
    const label = isExtraSmallWidth
        ? `${bytesInGB(usage)} /  ${bytesInGB(storage)} ${t("storage_unit.gb")} ${t("used")}`
        : `${formattedStorageByteSize(usage, { round: true })} ${t("of")} ${formattedStorageByteSize(storage)} ${t("used")}`;

    return (
        <Box>
            <Typography variant="small" sx={{ opacity: 0.7 }}>
                {t("storage")}
            </Typography>
            <Typography variant="h3">{label}</Typography>
        </Box>
    );
};

interface UsageStorage {
    usage: number;
    storage: number;
}

type IndividualUsageSectionProps = UsageStorage & { fileCount: number };

const IndividualUsageSection: React.FC<IndividualUsageSectionProps> = ({
    usage,
    storage,
    fileCount,
}) => (
    // Use the unsuffixed key as the fallback for languages with more plural forms.
    <Stack sx={{ gap: 1.5 }}>
        <UsageBar>
            <UsageBarSegment
                {...{ usage, storage }}
                fillColor="rgba(255 255 255 / 1)"
            />
        </UsageBar>
        <Stack direction="row" sx={{ justifyContent: "space-between" }}>
            <Typography variant="mini">
                {`${formattedStorageByteSize(storage - usage)} ${t("free")}`}
            </Typography>
            <Typography variant="mini" sx={{ fontWeight: "medium" }}>
                {t("photos_count", { count: fileCount })}
            </Typography>
        </Stack>
    </Stack>
);

const UsageBar = styled("div")`
    position: relative;
    height: 4px;
    border-radius: 4px;
    background-color: rgba(255 255 255 / 0.2);
`;

type UsageBarSegmentProps = UsageStorage & { fillColor: string };

const UsageBarSegment: React.FC<UsageBarSegmentProps> = ({
    usage,
    storage,
    fillColor,
}) => (
    <Box
        sx={{
            position: "absolute",
            left: 0,
            top: 0,
            width: "max(var(--et-width), 2px)",
            height: "4px",
            borderRadius: "4px",
            backgroundColor: "var(--et-background-color)",
        }}
        style={
            {
                "--et-width": `${storage > 0 ? Math.min(usage / storage, 1) * 100 : 0}%`,
                "--et-background-color": fillColor,
            } as React.CSSProperties
        }
    />
);

const FamilySubscriptionCardContents: React.FC<
    SubscriptionCardContentOverlayProps
> = ({ userDetails }) => {
    const usage = familyUsage(userDetails);
    const storage =
        (userDetails.familyData?.storage ?? 0) + userDetails.storageBonus;

    return (
        <>
            <StorageSection {...{ storage, usage }} />
            <FamilyUsageSection
                userUsage={userDetails.usage}
                fileCount={userDetails.fileCount}
                {...{ storage, usage }}
            />
        </>
    );
};

type FamilyUsageSectionProps = UsageStorage & {
    userUsage: number;
    fileCount: number;
};

const FamilyUsageSection: React.FC<FamilyUsageSectionProps> = ({
    usage,
    storage,
    userUsage,
    fileCount,
}) => (
    <Stack sx={{ gap: 1.5 }}>
        <UsageBar>
            <UsageBarSegment
                {...{ storage }}
                usage={userUsage}
                fillColor="rgba(255 255 255 / 1)"
            />
            <UsageBarSegment
                {...{ usage, storage }}
                fillColor="rgba(255 255 255 / 0.6)"
            />
        </UsageBar>
        <Stack direction="row" sx={{ justifyContent: "space-between" }}>
            <Stack direction="row" sx={{ gap: 1.5 }}>
                <Legend label={t("you")} opacity={1} />
                <Legend label={t("family")} opacity={0.8} />
            </Stack>
            <Typography variant="mini" sx={{ fontWeight: "medium" }}>
                {t("photos_count", { count: fileCount })}
            </Typography>
        </Stack>
    </Stack>
);

interface LegendProps {
    label: string;
    opacity: number;
}

const Legend: React.FC<LegendProps> = ({ label, opacity }) => (
    <Stack direction="row" sx={{ alignItems: "center", opacity }}>
        <LegendDot />
        <Typography variant="mini" sx={{ fontWeight: "medium" }}>
            {label}
        </Typography>
    </Stack>
);

const LegendDot = styled(CircleIcon)`
    font-size: 8.71px;
    margin: 0;
    margin-inline-end: 4px;
    color: inherit;
`;
