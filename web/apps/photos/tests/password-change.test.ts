import { changePassword } from "ente-accounts/services/password";
import { PasswordWorker } from "ente-accounts/services/password.worker";
import { afterEach, beforeEach, expect, test, vi } from "vitest";

const {
    generateInteractiveKeyAttributes,
    getAndSaveSRPAttributes,
    keyAttributes,
    rustChangePassword,
    saveKeyAttributes,
    warn,
} = vi.hoisted(() => ({
    generateInteractiveKeyAttributes: vi.fn(),
    getAndSaveSRPAttributes: vi.fn(),
    keyAttributes: { kekSalt: "old-salt" },
    rustChangePassword: vi.fn(),
    saveKeyAttributes: vi.fn(),
    warn: vi.fn(),
}));

vi.mock("comlink", () => ({ expose: vi.fn() }));
vi.mock("ente-base/log", () => ({ default: { warn } }));
vi.mock("ente-base/log-web", () => ({
    logUnhandledErrorsAndRejectionsInWorker: vi.fn(),
}));
vi.mock("ente-base/token", () => ({
    ensureAuthToken: () => Promise.resolve("token"),
}));
vi.mock("ente-base/session-storage", () => ({
    readMasterKeyFromSession: () => Promise.resolve("master-key"),
}));
vi.mock("ente-base/worker/comlink-worker", () => ({
    ComlinkWorker: class {
        remote = Promise.resolve(new PasswordWorker());
        terminate = vi.fn();
    },
}));
vi.mock("ente-accounts/services/accounts-client", () => ({
    withAccountsClient: (operation: (client: unknown) => Promise<unknown>) =>
        operation({}),
}));
vi.mock("ente-accounts/services/accounts-db", () => ({ saveKeyAttributes }));
vi.mock("ente-accounts/services/key-attributes", () => ({
    generateInteractiveKeyAttributes,
}));
vi.mock("ente-accounts/services/srp", () => ({ getAndSaveSRPAttributes }));
vi.mock("ente-accounts/services/user", () => ({
    ensureLocalUser: () => ({ email: "user@example.org" }),
    ensureSavedKeyAttributes: () => keyAttributes,
}));
vi.mock("ente-prelogin-wasm", () => ({ changePassword: rustChangePassword }));

const updated = { kekSalt: "new-local-salt" };
const change = () => changePassword("new-password", vi.fn());

afterEach(() => vi.unstubAllGlobals());

beforeEach(() => {
    vi.resetAllMocks();
    vi.stubGlobal("Worker", vi.fn());
    generateInteractiveKeyAttributes.mockResolvedValue(updated);
    rustChangePassword.mockResolvedValue({ kekSalt: "new-remote-salt" });
    getAndSaveSRPAttributes.mockResolvedValue(undefined);
});

test("SRP refresh failure preserves a successful password change", async () => {
    const refreshError = new Error("offline");
    getAndSaveSRPAttributes.mockRejectedValue(refreshError);

    await expect(change()).resolves.toBeUndefined();

    expect(generateInteractiveKeyAttributes).toHaveBeenCalledBefore(
        rustChangePassword,
    );
    expect(saveKeyAttributes).toHaveBeenCalledWith(updated);
    expect(saveKeyAttributes).toHaveBeenCalledBefore(getAndSaveSRPAttributes);
    expect(warn).toHaveBeenCalledWith(expect.any(String), refreshError);
});

test("local key preparation failure does not change the password", async () => {
    const error = new Error("key preparation failed");
    generateInteractiveKeyAttributes.mockRejectedValue(error);

    await expect(change()).rejects.toBe(error);

    expect(rustChangePassword).not.toHaveBeenCalled();
    expect(saveKeyAttributes).not.toHaveBeenCalled();
});

test("failed password update preserves the existing local keys", async () => {
    const error = new Error("update rejected");
    rustChangePassword.mockRejectedValue(error);

    await expect(change()).rejects.toBe(error);

    expect(saveKeyAttributes).not.toHaveBeenCalled();
    expect(getAndSaveSRPAttributes).not.toHaveBeenCalled();
});
