import { Box } from "@mui/material";
import { SpaceAvatarCropPage } from "components/SpaceAvatarCropPage";
import { SpaceAvatarEditButton } from "components/SpaceAvatarEditButton";
import { SpaceAvatarImage } from "components/SpaceAvatarImage";
import { SpaceBackIcon } from "components/SpaceBackIcon";
import { SpaceButtonSpinner } from "components/SpaceButtonSpinner";
import React, { useEffect, useRef, useState } from "react";
import type { Area, Point } from "react-easy-crop";
import { spaceTouchTargetSize } from "styles/touchTargets";
import {
    prepareSpaceAvatarImageFromCrop,
    spaceAvatarCropImageForFile,
    spaceAvatarImageErrorMessage,
    spaceAvatarImageInputAccept,
} from "utils/spacePostImage";

export const setupProfileBackground = "#FAFAFA";

const green = "#08C225";
const textBase = "#000";
const textLight = "#969696";
const warning = "#F63A3A";
const setupProfileFormID = "space-setup-profile-form";

export interface SetupProfile {
    avatarUrl: string | null;
    avatarKeyVersion?: number;
    avatarObjectID?: string;
    avatarUpdatedAt?: string;
    coverUrl?: string | null;
    coverKeyVersion?: number;
    coverObjectID?: string;
    coverUpdatedAt?: string;
    fullName: string;
    username: string;
    spaceId?: string;
    spaceSlug?: string;
}

export interface SetupProfileInput extends SetupProfile {
    avatarFile?: File | null;
    coverFile?: File | null;
}

interface AvatarCropImage {
    file: File;
    url: string;
}

interface SetupProfileScreenProps {
    ctaLabel?: string;
    errorMessage?: string;
    isSubmitting?: boolean;
    onBack: () => void;
    onContinue?: (profile: SetupProfileInput) => Promise<void> | void;
    onUsernameChange?: (username: string) => void;
    usernameStatus?: "available" | "unavailable";
}

interface TextInputProps {
    endAdornment?: React.ReactNode;
    id: string;
    label: string;
    onChange?: (value: string) => void;
    placeholder?: string;
    required?: boolean;
    startAdornment?: React.ReactNode;
    value?: string;
}

const AvatarPlaceholder: React.FC = () => (
    <SpaceAvatarImage aria-hidden border="4px solid white" borderRadius="50%" />
);

const CheckIcon: React.FC = () => (
    <Box
        component="svg"
        viewBox="0 0 20 20"
        aria-hidden
        sx={{ display: "block", height: 18, width: 18 }}
    >
        <path
            d="M4.5 10.25L8.25 14L15.75 6"
            fill="none"
            stroke="currentColor"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="2.2"
        />
    </Box>
);

const XIcon: React.FC = () => (
    <Box
        component="svg"
        viewBox="0 0 20 20"
        aria-hidden
        sx={{ display: "block", height: 18, width: 18 }}
    >
        <path
            d="M5.75 5.75L14.25 14.25M14.25 5.75L5.75 14.25"
            fill="none"
            stroke="currentColor"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="2.2"
        />
    </Box>
);

const UsernameStatusIcon: React.FC<{
    status?: "available" | "unavailable";
}> = ({ status }) => {
    if (!status) return null;

    return (
        <Box
            component="span"
            role="img"
            aria-label={
                status == "available"
                    ? "Username available"
                    : "Username unavailable"
            }
            sx={{
                alignItems: "center",
                color: status == "available" ? green : warning,
                display: "flex",
                flexShrink: 0,
                justifyContent: "center",
                ml: 1,
            }}
        >
            {status == "available" ? <CheckIcon /> : <XIcon />}
        </Box>
    );
};

const TextInput: React.FC<TextInputProps> = ({
    endAdornment,
    id,
    label,
    onChange,
    placeholder,
    required,
    startAdornment,
    value,
}) => (
    <Box sx={{ width: "100%" }}>
        <Box
            component="label"
            htmlFor={id}
            sx={{
                color: textBase,
                display: "flex",
                fontFamily: '"Inter Variable", Inter, sans-serif',
                fontSize: 14,
                fontWeight: 500,
                gap: "2px",
                lineHeight: "20px",
                mb: "9px",
            }}
        >
            {label}
            {required && (
                <Box component="span" sx={{ color: warning }}>
                    *
                </Box>
            )}
        </Box>
        <Box
            sx={{
                alignItems: "center",
                bgcolor: "white",
                borderRadius: "16px",
                display: "flex",
                height: 52,
                px: 2,
                width: "100%",
                "&:focus-within": {
                    outline: `2px solid ${green}`,
                    outlineOffset: 1,
                },
            }}
        >
            {startAdornment}
            <Box
                component="input"
                id={id}
                onChange={(event) => onChange?.(event.target.value)}
                placeholder={placeholder}
                type="text"
                value={value}
                sx={{
                    bgcolor: "transparent",
                    border: 0,
                    color: textBase,
                    flex: 1,
                    fontFamily: '"Inter Variable", Inter, sans-serif',
                    fontSize: 14,
                    fontWeight: 500,
                    lineHeight: "20px",
                    minWidth: 0,
                    outline: 0,
                    p: 0,
                    "&::placeholder": { color: textLight, opacity: 1 },
                }}
            />
            {endAdornment}
        </Box>
    </Box>
);

export const SetupProfileScreen: React.FC<SetupProfileScreenProps> = ({
    ctaLabel = "Next",
    errorMessage,
    isSubmitting = false,
    onBack,
    onContinue,
    onUsernameChange,
    usernameStatus,
}) => {
    const [avatarFile, setAvatarFile] = useState<File | null>(null);
    const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
    const [avatarCropImage, setAvatarCropImage] =
        useState<AvatarCropImage | null>(null);
    const [avatarCrop, setAvatarCrop] = useState<Point>({ x: 0, y: 0 });
    const [avatarCropPixels, setAvatarCropPixels] = useState<Area | null>(null);
    const [avatarError, setAvatarError] = useState<string>();
    const [avatarZoom, setAvatarZoom] = useState(1);
    const [username, setUsername] = useState("");
    const [fullName, setFullName] = useState("");
    const [isApplyingAvatarCrop, setIsApplyingAvatarCrop] = useState(false);
    const [isPreparingAvatar, setIsPreparingAvatar] = useState(false);
    const avatarCropUrlRef = useRef<string | null>(null);
    const avatarUrlRef = useRef<string | null>(null);
    const avatarSelectionIDRef = useRef(0);
    const fileInputRef = useRef<HTMLInputElement | null>(null);

    const canContinue =
        !isSubmitting &&
        !avatarCropImage &&
        !isApplyingAvatarCrop &&
        !isPreparingAvatar &&
        usernameStatus != "unavailable" &&
        username.trim().length > 0 &&
        fullName.trim().length > 0;
    const isContinueButtonActive = canContinue || isSubmitting;

    useEffect(
        () => () => {
            if (avatarCropUrlRef.current) {
                URL.revokeObjectURL(avatarCropUrlRef.current);
            }
            if (avatarUrlRef.current) URL.revokeObjectURL(avatarUrlRef.current);
        },
        [],
    );

    const clearAvatarCropImage = () => {
        avatarSelectionIDRef.current += 1;
        if (avatarCropUrlRef.current) {
            URL.revokeObjectURL(avatarCropUrlRef.current);
            avatarCropUrlRef.current = null;
        }
        setAvatarCropImage(null);
        setAvatarCrop({ x: 0, y: 0 });
        setAvatarCropPixels(null);
        setAvatarZoom(1);
        setIsApplyingAvatarCrop(false);
        setIsPreparingAvatar(false);
    };

    const prepareSelectedAvatar = async (file: File, selectionID: number) => {
        setIsPreparingAvatar(true);
        if (avatarCropUrlRef.current) {
            URL.revokeObjectURL(avatarCropUrlRef.current);
            avatarCropUrlRef.current = null;
        }
        setAvatarCropImage(null);
        setAvatarCropPixels(null);
        try {
            const cropImage = await spaceAvatarCropImageForFile(file);
            if (avatarSelectionIDRef.current != selectionID) {
                URL.revokeObjectURL(cropImage.url);
                return;
            }

            avatarCropUrlRef.current = cropImage.url;
            setAvatarCrop({ x: 0, y: 0 });
            setAvatarCropImage({ file, url: cropImage.url });
            setAvatarCropPixels(null);
            setAvatarError(undefined);
            setAvatarZoom(1);
        } catch (error) {
            if (avatarSelectionIDRef.current != selectionID) return;
            console.error("Failed to prepare space avatar", error);
            setAvatarError(spaceAvatarImageErrorMessage(error));
        } finally {
            if (avatarSelectionIDRef.current == selectionID) {
                setIsPreparingAvatar(false);
            }
        }
    };

    const handleAvatarChange: React.ChangeEventHandler<HTMLInputElement> = (
        event,
    ) => {
        const file = event.target.files?.[0];
        event.target.value = "";
        if (!file) return;

        const selectionID = avatarSelectionIDRef.current + 1;
        avatarSelectionIDRef.current = selectionID;
        setAvatarError(undefined);

        void prepareSelectedAvatar(file, selectionID);
    };

    const applyAvatarCrop = async () => {
        if (!avatarCropImage || !avatarCropPixels) return;

        setIsApplyingAvatarCrop(true);
        try {
            const avatar = await prepareSpaceAvatarImageFromCrop(
                avatarCropImage.file,
                avatarCropImage.url,
                avatarCropPixels,
            );

            if (avatarUrlRef.current) URL.revokeObjectURL(avatarUrlRef.current);
            const nextUrl = URL.createObjectURL(avatar.file);
            avatarUrlRef.current = nextUrl;
            setAvatarError(undefined);
            setAvatarFile(avatar.file);
            setAvatarUrl(nextUrl);
            clearAvatarCropImage();
        } catch (error) {
            console.error("Failed to crop space avatar", error);
            setAvatarError(spaceAvatarImageErrorMessage(error));
            setIsApplyingAvatarCrop(false);
        }
    };

    const handleUsernameChange = (value: string) => {
        setUsername(value);
        onUsernameChange?.(value);
    };

    const submitProfile = () => {
        if (canContinue) {
            void onContinue?.({
                avatarFile,
                avatarUrl,
                fullName: fullName.trim(),
                username: username.trim(),
            });
        }
    };

    const handleSubmit: React.SubmitEventHandler<HTMLFormElement> = (event) => {
        event.preventDefault();
        submitProfile();
    };

    const avatarFileInput = (
        <Box
            component="input"
            ref={fileInputRef}
            accept={spaceAvatarImageInputAccept}
            onChange={handleAvatarChange}
            type="file"
            sx={{ display: "none" }}
        />
    );

    if (avatarCropImage) {
        return (
            <>
                {avatarFileInput}
                <SpaceAvatarCropPage
                    background={setupProfileBackground}
                    crop={avatarCrop}
                    errorMessage={avatarError}
                    imageURL={avatarCropImage.url}
                    isDoneDisabled={!avatarCropPixels}
                    isSaving={isApplyingAvatarCrop}
                    onBack={clearAvatarCropImage}
                    onChooseAnother={() => fileInputRef.current?.click()}
                    onCropChange={setAvatarCrop}
                    onCropComplete={(_croppedArea, croppedAreaPixels) =>
                        setAvatarCropPixels(croppedAreaPixels)
                    }
                    onDone={() => void applyAvatarCrop()}
                    onZoomChange={setAvatarZoom}
                    zoom={avatarZoom}
                />
            </>
        );
    }

    return (
        <Box
            component="main"
            sx={{
                bgcolor: setupProfileBackground,
                color: textBase,
                display: "grid",
                minHeight: "100svh",
                overflow: "hidden",
                placeItems: { xs: "stretch", sm: "start center" },
            }}
        >
            <Box
                sx={{
                    bgcolor: setupProfileBackground,
                    boxSizing: "border-box",
                    display: "flex",
                    flexDirection: "column",
                    minHeight: "100svh",
                    mx: "auto",
                    pb: "120px",
                    px: 3,
                    width: "100%",
                    "@media (min-width: 600px)": { maxWidth: 390 },
                }}
            >
                <Box
                    component="header"
                    sx={{
                        display: "grid",
                        gridTemplateColumns: `${spaceTouchTargetSize}px 1fr ${spaceTouchTargetSize}px`,
                        height: spaceTouchTargetSize,
                        mt: "32px",
                        width: "100%",
                    }}
                >
                    <Box
                        component="button"
                        type="button"
                        aria-label="Back"
                        onClick={onBack}
                        sx={{
                            alignItems: "center",
                            bgcolor: "transparent",
                            border: 0,
                            color: textBase,
                            cursor: "pointer",
                            display: "flex",
                            height: spaceTouchTargetSize,
                            justifyContent: "flex-start",
                            p: 0,
                            width: spaceTouchTargetSize,
                            "&:focus-visible": {
                                borderRadius: "50%",
                                outline: `2px solid ${green}`,
                                outlineOffset: 2,
                            },
                        }}
                    >
                        <SpaceBackIcon />
                    </Box>
                    <Box
                        component="h1"
                        sx={{
                            alignSelf: "center",
                            fontFamily: '"Inter Variable", Inter, sans-serif',
                            fontSize: 20,
                            fontWeight: 600,
                            justifySelf: "center",
                            lineHeight: "28px",
                            m: 0,
                            whiteSpace: "nowrap",
                        }}
                    >
                        Setup your profile
                    </Box>
                    <Box />
                </Box>

                <Box
                    component="form"
                    id={setupProfileFormID}
                    noValidate
                    onSubmit={handleSubmit}
                    sx={{
                        alignItems: "center",
                        display: "flex",
                        flexDirection: "column",
                        mt: "32px",
                        width: "100%",
                    }}
                >
                    {avatarFileInput}

                    <Box sx={{ position: "relative" }}>
                        <Box
                            component="button"
                            type="button"
                            aria-label={
                                avatarUrl
                                    ? "Change profile picture"
                                    : "Upload profile picture"
                            }
                            disabled={isPreparingAvatar}
                            onClick={() => fileInputRef.current?.click()}
                            sx={{
                                alignItems: "center",
                                aspectRatio: "1 / 1",
                                bgcolor: "transparent",
                                border: 0,
                                borderRadius: "50%",
                                color: textBase,
                                cursor: isPreparingAvatar
                                    ? "default"
                                    : "pointer",
                                display: "flex",
                                height: 112,
                                justifyContent: "center",
                                overflow: "hidden",
                                p: 0,
                                position: "relative",
                                width: 112,
                                "&:focus-visible": {
                                    outline: `2px solid ${green}`,
                                    outlineOffset: 3,
                                },
                            }}
                        >
                            {isPreparingAvatar ? (
                                <Box
                                    component="span"
                                    sx={{
                                        color: textLight,
                                        fontFamily:
                                            '"Inter Variable", Inter, sans-serif',
                                        fontSize: 13,
                                        fontWeight: 600,
                                        lineHeight: "18px",
                                    }}
                                >
                                    Preparing...
                                </Box>
                            ) : avatarUrl ? (
                                <Box
                                    component="img"
                                    alt=""
                                    src={avatarUrl}
                                    sx={{
                                        display: "block",
                                        height: "100%",
                                        objectFit: "cover",
                                        objectPosition: "center",
                                        width: "100%",
                                    }}
                                />
                            ) : (
                                <AvatarPlaceholder />
                            )}
                        </Box>
                        <SpaceAvatarEditButton
                            disabled={isPreparingAvatar}
                            onClick={() => fileInputRef.current?.click()}
                        />
                    </Box>

                    <Box
                        sx={{
                            display: "flex",
                            flexDirection: "column",
                            gap: "24px",
                            mt: "52px",
                            width: "100%",
                        }}
                    >
                        <TextInput
                            endAdornment={
                                <UsernameStatusIcon status={usernameStatus} />
                            }
                            id="space-setup-profile-username"
                            label="Username"
                            onChange={handleUsernameChange}
                            placeholder="username"
                            required
                            startAdornment={
                                <Box
                                    component="span"
                                    aria-hidden
                                    sx={{
                                        color: textBase,
                                        flexShrink: 0,
                                        fontFamily:
                                            '"Inter Variable", Inter, sans-serif',
                                        fontSize: 14,
                                        fontWeight: 500,
                                        lineHeight: "20px",
                                    }}
                                >
                                    ente.space/
                                </Box>
                            }
                            value={username}
                        />
                        <TextInput
                            id="space-setup-profile-name"
                            label="Name"
                            onChange={setFullName}
                            placeholder="Enter your name"
                            required
                            value={fullName}
                        />
                        {(avatarError || errorMessage) && (
                            <Box
                                role="alert"
                                sx={{
                                    color: warning,
                                    fontFamily:
                                        '"Inter Variable", Inter, sans-serif',
                                    fontSize: 13,
                                    fontWeight: 500,
                                    lineHeight: "18px",
                                    mt: "-8px",
                                }}
                            >
                                {errorMessage ?? avatarError}
                            </Box>
                        )}
                    </Box>
                </Box>

                <Box
                    sx={{
                        bgcolor: setupProfileBackground,
                        bottom: 0,
                        boxSizing: "border-box",
                        left: "50%",
                        maxWidth: 390,
                        p: 3,
                        position: "fixed",
                        transform: "translateX(-50%)",
                        width: "100%",
                    }}
                >
                    <Box
                        className={
                            isContinueButtonActive ? "green-bg" : undefined
                        }
                        component="button"
                        form={setupProfileFormID}
                        type="submit"
                        disabled={!canContinue}
                        aria-label={isSubmitting ? "Saving" : undefined}
                        aria-busy={isSubmitting ? true : undefined}
                        sx={{
                            alignItems: "center",
                            bgcolor: isContinueButtonActive ? green : "#F5F5F5",
                            border: 0,
                            borderRadius: "20px",
                            color: isContinueButtonActive ? "white" : textLight,
                            cursor: canContinue ? "pointer" : "default",
                            display: "flex",
                            fontFamily: '"Inter Variable", Inter, sans-serif',
                            fontSize: 14,
                            fontWeight: 500,
                            height: 48,
                            justifyContent: "center",
                            lineHeight: "20px",
                            p: "14px 24px",
                            width: "100%",
                            "&:focus-visible": {
                                outline: `2px solid ${green}`,
                                outlineOffset: 3,
                            },
                            "&:hover": canContinue
                                ? { bgcolor: "#07AE22" }
                                : undefined,
                        }}
                    >
                        {isSubmitting ? <SpaceButtonSpinner /> : ctaLabel}
                    </Box>
                </Box>
            </Box>
        </Box>
    );
};
