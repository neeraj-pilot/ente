import {
    HTTPError,
    isMuseumHTTPError,
    retryAsyncOperation,
} from "ente-base/http";
import { wait } from "ente-utils/promise";
import { beforeEach, describe, expect, test, vi } from "vitest";

vi.mock("ente-base/app", () => ({
    clientPackageName: "io.ente.photos.desktop",
    desktopAppVersion: "test",
    isDesktop: true,
}));
vi.mock("ente-base/token", () => ({ ensureAuthToken: vi.fn() }));
vi.mock("ente-base/log", () => ({
    default: { error: vi.fn(), warn: vi.fn() },
}));
vi.mock("ente-utils/promise", () => ({
    wait: vi.fn().mockResolvedValue(undefined),
}));

const responseError = (status: number, body: string) => {
    const response = new Response(body, { status });
    Object.defineProperty(response, "url", {
        value: "https://api.example.test/files",
    });
    return new HTTPError(response);
};

beforeEach(() => vi.clearAllMocks());

describe("Museum error compatibility", () => {
    test("recognizes the conflict code without consuming the response", async () => {
        const body = JSON.stringify({ code: "FILE_UPLOAD_CONFLICT" });
        const error = responseError(400, body);

        await expect(
            isMuseumHTTPError(error, 400, "FILE_UPLOAD_CONFLICT"),
        ).resolves.toBe(true);

        expect(await error.res.text()).toBe(body);
    });

    test.each([
        ["legacy server", 400, "{}"],
        ["empty response", 400, ""],
        ["non-JSON response", 400, "Bad Request"],
        ["unknown code", 400, '{"code":"OTHER_ERROR"}'],
        ["invalid code", 400, '{"code":123}'],
        ["null response", 400, "null"],
        ["wrong status", 503, '{"code":"FILE_UPLOAD_CONFLICT"}'],
    ])("does not classify %s as a conflict", async (_name, status, body) => {
        const error = responseError(status, body);

        await expect(
            isMuseumHTTPError(error, 400, "FILE_UPLOAD_CONFLICT"),
        ).resolves.toBe(false);
    });
});

describe("retryAsyncOperation", () => {
    test("preserves the retry schedule without an abort hook", async () => {
        const error = responseError(400, '{"code":"FILE_UPLOAD_CONFLICT"}');
        const request = vi.fn().mockRejectedValue(error);

        await expect(retryAsyncOperation(request)).rejects.toBe(error);

        expect(request).toHaveBeenCalledTimes(4);
        expect(vi.mocked(wait).mock.calls).toEqual([[2000], [5000], [10000]]);
    });

    test("continues retrying when the asynchronous abort hook resolves", async () => {
        const error = new TypeError("Failed to fetch");
        const request = vi
            .fn()
            .mockRejectedValueOnce(error)
            .mockResolvedValueOnce({ id: 123 });
        const abortIfNeeded = vi.fn().mockResolvedValue(undefined);

        await expect(
            retryAsyncOperation(request, { abortIfNeeded }),
        ).resolves.toEqual({ id: 123 });

        expect(abortIfNeeded).toHaveBeenCalledWith(error);
        expect(request).toHaveBeenCalledTimes(2);
        expect(vi.mocked(wait).mock.calls).toEqual([[2000]]);
    });

    test("stops without waiting when the asynchronous abort hook rejects", async () => {
        const error = responseError(400, "{}");
        const request = vi.fn().mockRejectedValue(error);
        const abortIfNeeded = vi.fn().mockRejectedValue(error);

        await expect(
            retryAsyncOperation(request, { abortIfNeeded }),
        ).rejects.toBe(error);

        expect(abortIfNeeded).toHaveBeenCalledWith(error);
        expect(request).toHaveBeenCalledTimes(1);
        expect(wait).not.toHaveBeenCalled();
    });

    test("preserves synchronous abort hooks and their mapped errors", async () => {
        const error = responseError(401, "{}");
        const sessionExpired = new Error("Session expired");
        const request = vi.fn().mockRejectedValue(error);
        const abortIfNeeded = vi.fn(() => {
            throw sessionExpired;
        });

        await expect(
            retryAsyncOperation(request, { abortIfNeeded }),
        ).rejects.toBe(sessionExpired);

        expect(abortIfNeeded).toHaveBeenCalledWith(error);
        expect(request).toHaveBeenCalledTimes(1);
        expect(wait).not.toHaveBeenCalled();
    });
});
