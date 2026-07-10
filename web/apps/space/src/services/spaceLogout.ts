import { accountLogout } from "ente-accounts-rs/services/logout";
import { revokeSpaceBrowserSessions } from "services/spacePersistentSession";

export const spaceLogout = async () => {
    if (await revokeSpaceBrowserSessions()) await accountLogout();
};
