const keyTag =
    /^#EXT-X-KEY:METHOD=AES-128,URI="data:text\/plain;base64,[A-Za-z0-9+/]{22}==",IV=0x[0-9a-fA-F]{32}$/;
const durationTag = /^#EXTINF:\d+(?:\.\d+)?,$/;
const byteRangeTag = /^#EXT-X-BYTERANGE:\d+@\d+$/;

export const reconstructHLSPlaylist = (
    template: string,
    segmentURL: string,
) => {
    const lines = template
        .split("\n")
        .filter((line) => !line.startsWith("#") || line.startsWith("#EXT"));
    if (lines.at(-1) == "") lines.pop();

    if (
        lines.length < 9 ||
        lines[0] != "#EXTM3U" ||
        lines[1] != "#EXT-X-VERSION:4" ||
        !/^#EXT-X-TARGETDURATION:\d+$/.test(lines[2]!) ||
        lines[3] != "#EXT-X-MEDIA-SEQUENCE:0" ||
        !keyTag.test(lines[4]!) ||
        lines.at(-1) != "#EXT-X-ENDLIST" ||
        (lines.length - 6) % 3
    ) {
        return undefined;
    }

    const result = lines.slice(0, 5);
    for (let i = 5; i < lines.length - 1; i += 3) {
        if (
            !durationTag.test(lines[i]!) ||
            !byteRangeTag.test(lines[i + 1]!) ||
            lines[i + 2] != "output.ts"
        ) {
            return undefined;
        }
        result.push(lines[i]!, lines[i + 1]!, segmentURL);
    }

    return [...result, "#EXT-X-ENDLIST", ""].join("\n");
};
