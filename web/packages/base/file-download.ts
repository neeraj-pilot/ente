import { z } from "zod";
import {
    authenticatedRequestHeaders,
    ensureOk,
    publicRequestHeaders,
} from "./http";
import { apiURL } from "./origins";
import { ensureAuthToken } from "./token";

const FileURLResponse = z.object({ url: z.string() });
const V3_RETRY_DELAY_MS = 60 * 60 * 1000;
let v3RetryAfter = 0;

export const fetchFile = async (fileID: number, kind: "file" | "thumbnail") => {
    if (v3RetryAfter <= Date.now()) {
        const path = kind == "file" ? "download" : "thumbnail";
        const res = await fetch(await apiURL(`/files/${path}/v3/${fileID}`), {
            headers: await authenticatedRequestHeaders(),
        });
        if (res.status !== 404) {
            ensureOk(res);
            const { url } = FileURLResponse.parse(await res.json());
            return fetch(url);
        }
        v3RetryAfter = Date.now() + V3_RETRY_DELAY_MS;
    }

    // migration : 3 aug 2026
    const token = await ensureAuthToken();
    const path = kind == "file" ? "download" : "preview";
    return fetch(await apiURL(`/files/${path}/${fileID}`, { token }), {
        headers: publicRequestHeaders(),
    });
};
