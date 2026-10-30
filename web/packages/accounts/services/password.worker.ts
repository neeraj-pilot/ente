import { expose } from "comlink";
import { logUnhandledErrorsAndRejectionsInWorker } from "ente-base/log-web";
import { ensureAuthToken } from "ente-base/token";
import { changePassword } from "ente-prelogin-wasm";
import { z } from "zod";
import { withAccountsClient } from "./accounts-client";
import { generateInteractiveKeyAttributes } from "./key-attributes";
import { RemoteSRPAttributes } from "./srp";
import { RemoteKeyAttributes, type KeyAttributes } from "./user";

const ChangedPassword = z.object({
    keyAttributes: RemoteKeyAttributes,
    srpAttributes: RemoteSRPAttributes,
});

export class PasswordWorker {
    async changePassword(
        password: string,
        masterKey: string,
        keyAttributes: KeyAttributes,
        email: string,
    ) {
        const token = await ensureAuthToken();
        const updated = ChangedPassword.parse(
            await withAccountsClient((client) =>
                changePassword(
                    client,
                    token,
                    email,
                    password,
                    masterKey,
                    keyAttributes,
                ),
            ),
        );
        return {
            srpAttributes: updated.srpAttributes,
            keyAttributes: await generateInteractiveKeyAttributes(
                password,
                updated.keyAttributes,
                masterKey,
            ),
        };
    }
}

expose(PasswordWorker);

logUnhandledErrorsAndRejectionsInWorker();
