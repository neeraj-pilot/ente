import { reconstructHLSPlaylist } from "ente-gallery/utils/hls";
import { describe, expect, test } from "vitest";

const playlist = `#EXTM3U
#EXT-X-VERSION:4
#EXT-X-TARGETDURATION:8
#EXT-X-MEDIA-SEQUENCE:0
#EXT-X-KEY:METHOD=AES-128,URI="data:text/plain;base64,XjvG7qeRrsOpPUbJPh2Ikg==",IV=0x00000000000000000000000000000000
#EXTINF:8.333333,
#EXT-X-BYTERANGE:3046928@0
output.ts
#EXTINF:2.200000,
#EXT-X-BYTERANGE:834736@3046928
output.ts
#EXT-X-ENDLIST
`;

describe("reconstructHLSPlaylist", () => {
    test("reconstructs a generated single-file playlist", () => {
        const segmentURL = "https://museum.example/video?token=secret";

        expect(reconstructHLSPlaylist(playlist, segmentURL)).toBe(
            playlist.replaceAll("output.ts", segmentURL),
        );
    });

    test.each([
        ["remote segment", playlist.replace("output.ts", "https://attacker")],
        [
            "remote key",
            playlist.replace(
                "data:text/plain;base64,XjvG7qeRrsOpPUbJPh2Ikg==",
                "https://attacker/key",
            ),
        ],
        [
            "map",
            playlist.replace(
                "#EXTINF:8.333333,",
                '#EXT-X-MAP:URI="https://attacker/map"\n#EXTINF:8.333333,',
            ),
        ],
    ])("rejects a playlist with an unexpected %s", (_, input) => {
        expect(
            reconstructHLSPlaylist(input, "https://museum/video"),
        ).toBeUndefined();
    });
});
