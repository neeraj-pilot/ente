import { expose } from "comlink";
import { logUnhandledErrorsAndRejectionsInWorker } from "ente-base/log-web";
import { ensureAuthToken } from "ente-base/token";
import { changePassword } from "ente-prelogin-wasm";
import { withAccountsClient } from "./accounts-client";
import { generateInteractiveKeyAttributes } from "./key-attributes";
import type { KeyAttributes } from "./user";

export class PasswordWorker {
    async changePassword(
        password: string,
        masterKey: string,
        keyAttributes: KeyAttributes,
    ) {
        const interactiveKeyAttributes = await generateInteractiveKeyAttributes(
            password,
            keyAttributes,
            masterKey,
        );
        const token = await ensureAuthToken();
        await withAccountsClient((client) =>
            changePassword(client, token, password, masterKey, keyAttributes),
        );
        return interactiveKeyAttributes;
    }
}

expose(PasswordWorker);

logUnhandledErrorsAndRejectionsInWorker();
