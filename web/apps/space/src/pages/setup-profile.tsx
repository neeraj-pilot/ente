import { SpacePageMeta } from "components/SpacePageMeta";
import { SpaceRouteFallback } from "components/SpaceRouteFallback";
import React, { useEffect, useMemo, useState } from "react";
import {
    SetupProfileScreen,
    setupProfileBackground,
} from "screens/SetupProfileScreen";
import { savedPendingSpaceInvite } from "services/spaceInvite";
import {
    saveSpaceProfile,
    spaceProfileErrorMessage,
    spaceUsernameAvailability,
    spaceUsernameValidationError,
} from "services/spaceProfile";
import { useSpaceAppState } from "state/spaceAppState";
import { acceptPendingSpaceInvite } from "utils/spacePendingInvite";
import { setupProfileSourceFromQuery, spaceRoutes } from "utils/spaceRoutes";
import { useSpaceRouter } from "utils/spaceRouteTransitions";

const Page: React.FC = () => {
    const router = useSpaceRouter();
    const {
        onboardingEntrySource,
        profile,
        profileLoadError,
        profileLoadStatus,
        refreshProfile,
        setProfile,
        setSkipNextHomeFeedSkeleton,
    } = useSpaceAppState();
    const backSource = setupProfileSourceFromQuery(router.query.from);
    const isAddFriendLinkOnboarding =
        onboardingEntrySource == "add-friend-link";
    const [setupError, setSetupError] = useState<string>();
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [draftUsername, setDraftUsername] = useState("");
    const [usernameStatus, setUsernameStatus] = useState<
        "available" | "unavailable"
    >();
    const existingProfileRoute = useMemo(
        () =>
            isAddFriendLinkOnboarding || backSource == "login"
                ? spaceRoutes.home
                : spaceRoutes.invite,
        [backSource, isAddFriendLinkOnboarding],
    );

    useEffect(() => {
        if (router.isReady) void refreshProfile();
    }, [refreshProfile, router.isReady]);

    useEffect(() => {
        if (profileLoadStatus == "ready" && profile) {
            void acceptPendingSpaceInvite()
                .catch((error: unknown) =>
                    console.error("Failed to accept pending invite", error),
                )
                .finally(() => void router.replace(existingProfileRoute));
        }
    }, [existingProfileRoute, profile, profileLoadStatus, router]);

    useEffect(() => {
        if (profileLoadStatus != "ready" || profile) return;

        const username = draftUsername.trim();
        if (!username) {
            setUsernameStatus(undefined);
            return;
        }

        const validationError = spaceUsernameValidationError(username);
        if (validationError) {
            setUsernameStatus("unavailable");
            return;
        }

        setUsernameStatus(undefined);

        let cancelled = false;
        const timeout = window.setTimeout(() => {
            void spaceUsernameAvailability(username)
                .then((availability) => {
                    if (cancelled) return;
                    setUsernameStatus(
                        availability == "taken" ? "unavailable" : "available",
                    );
                })
                .catch((error: unknown) => {
                    console.error("Username availability check failed", error);
                    if (!cancelled) {
                        setUsernameStatus(undefined);
                    }
                });
        }, 350);

        return () => {
            cancelled = true;
            window.clearTimeout(timeout);
        };
    }, [draftUsername, profile, profileLoadStatus]);

    if (profileLoadStatus != "ready" || profile) {
        return (
            <SpaceRouteFallback
                background={setupProfileBackground}
                message={profileLoadError}
            />
        );
    }

    return (
        <>
            <SpacePageMeta themeColor={setupProfileBackground} />
            <SetupProfileScreen
                ctaLabel={isAddFriendLinkOnboarding ? "Done" : "Next"}
                errorMessage={setupError}
                isSubmitting={isSubmitting}
                onBack={() =>
                    void router.push(
                        backSource == "login"
                            ? spaceRoutes.login
                            : spaceRoutes.verify,
                    )
                }
                onContinue={async (nextProfile) => {
                    setIsSubmitting(true);
                    setSetupError(undefined);
                    try {
                        const savedProfile = await saveSpaceProfile(
                            nextProfile,
                            savedPendingSpaceInvite()?.spaceId,
                        );
                        const acceptedInvite = await acceptPendingSpaceInvite();
                        setProfile(savedProfile);
                        if (!acceptedInvite) {
                            setSkipNextHomeFeedSkeleton(true);
                        }
                        void router.push(
                            isAddFriendLinkOnboarding
                                ? spaceRoutes.home
                                : spaceRoutes.invite,
                        );
                    } catch (error) {
                        console.error("Space profile setup failed", error);
                        setSetupError(spaceProfileErrorMessage(error));
                        setIsSubmitting(false);
                    }
                }}
                onUsernameChange={setDraftUsername}
                usernameStatus={usernameStatus}
            />
        </>
    );
};

export default Page;
