import { expect, test } from "vitest";
import {
    boxSealOpenBytes,
    parseDeleteAccountChallenge,
    twoFactorRecoveryProof,
    validateAuthToken,
} from "../index";

const keyPair = {
    publicKey: "W/Vcc7guviK+gPNDBmevVw+uJVamQV5rMNQGUwCqlH0=",
    privateKey: "UEatwduoOIZ7K7v90MNCPli1eXC1JnqQ9XlgkkqH8ZY=",
};

test("opens a libsodium sealed token as bytes", async () => {
    const token = await boxSealOpenBytes(
        "jVHae52Eixf5JkF0B0jZYR0U/KQ8ckCM631dw253O0bPMmpGHRPieCG/KRKk075x4STFEptJDw==",
        keyPair,
    );
    expect(token).toStrictEqual(new Uint8Array([0, 1, 127, 128, 254, 255, 16]));
});

test("Rust auth bindings accept each payload only for its purpose", async () => {
    const tokenCipher =
        "BpFBq0d0Cvg5UO3phwRdNmcTHuQu6Pw5ibUXaXXnGGFYy0pmmSVXC57oAUu3mGk/CUsNdTIKAM1EEsllw6SiwWNHy5seNhc22Kk+UyvwCdxFvzHgwll+KCGh";
    const deletionCipher =
        "HEL5WY2HxHH07zzDqS+DFDnJTgDdVsH7EH7H1r/FxhSnkMtVVOE4TgZrBlMfZA7PzAr/wGnxbz2BuDv1sH0vMflhiepDfhs4JZ3Wk3w7cETJlQ==";
    const token = await boxSealOpenBytes(tokenCipher, keyPair);
    const deletion = await boxSealOpenBytes(deletionCipher, keyPair);
    await expect(validateAuthToken(token)).resolves.toBeUndefined();
    expect(token).toEqual(
        new Uint8Array([
            ...new TextEncoder().encode("auth-token"),
            ...Array.from({ length: 32 }, (_, i) => i),
        ]),
    );
    expect(await parseDeleteAccountChallenge(deletion)).toBe(
        "header.payload.signature",
    );
    await expect(validateAuthToken(deletion)).rejects.toThrow(
        "Invalid login token",
    );
    await expect(parseDeleteAccountChallenge(token)).rejects.toThrow(
        "Invalid deletion challenge",
    );
});

test.each([
    [
        "totp",
        "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
        "GLPadofZrER+5+7OLkqiNxlmyS6XzaKGdsMeHg1lnvU=",
    ],
    [
        "passkey",
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "PxcSH0NrKUX3u330jU6TtYp/I/mT4087vajBXIdPh0I=",
    ],
] as const)(
    "%s proof crosses the WASM binding correctly",
    async (factor, secret, expected) => {
        expect(
            await twoFactorRecoveryProof(factor, "recovery-session", secret),
        ).toBe(expected);
    },
);
