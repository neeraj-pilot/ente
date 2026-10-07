import { expect, test } from "vitest";
import {
    postPhotoLayout,
    profilePhotoGap,
} from "../src/utils/profile-photo-layout";

const photos = (ratios: number[]) =>
    ratios.map((aspectRatio, id) => ({ id: String(id), aspectRatio }));

test.each([
    { count: 2, rowSizes: [2] },
    { count: 3, rowSizes: [3] },
    { count: 4, rowSizes: [2, 2] },
])("the adaptive grid fits $count photos into rows", ({ count, rowSizes }) => {
    const tiles = photos(new Array<number>(count).fill(2 / 3));
    const rows = postPhotoLayout(tiles, 352).rows;
    expect(rows.map(({ tiles }) => tiles.length)).toEqual(rowSizes);
    expect(rows.flatMap(({ tiles }) => tiles)).toEqual(tiles);
});

test("the adaptive grid arranges five landscape photos", () => {
    const tiles = photos([3 / 2, 3 / 2, 3 / 2, 3 / 2, 3 / 2]);
    const rows = postPhotoLayout(tiles, 352).rows;
    expect(rows.map(({ tiles }) => tiles.map(({ id }) => id))).toEqual([
        ["0", "1"],
        ["2", "3"],
        ["4"],
    ]);
});

test("three portraits share a compact row", () => {
    expect(
        postPhotoLayout(
            photos([2 / 3, 3 / 4, 2 / 3, 2 / 3, 3 / 4, 2 / 3]),
            328,
        ).rows.map(({ tiles }) => tiles.length),
    ).toEqual([3, 3]);
});

test.each([
    { ratios: [3 / 4, 0.45], count: 2 },
    { ratios: [0.3, 0.4, 0.5], count: 3 },
])(
    "a row of $count tall portraits fills the grid without cropping",
    ({ ratios }) => {
        const width = 328;
        const rows = postPhotoLayout(photos(ratios), width).rows;
        expect(rows).toHaveLength(1);
        const row = rows[0]!;
        expect(row.width).toBe(width);
        expect(row.height).toBeGreaterThan(width * 0.75);
        expect(
            row.tiles.reduce(
                (sum, tile) => sum + tile.aspectRatio * row.height,
                0,
            ) +
                (row.tiles.length - 1) * profilePhotoGap,
        ).toBeCloseTo(width);
    },
);

test("a lone portrait within a larger grid keeps its height limit", () => {
    const rows = postPhotoLayout(photos([8, 0.25]), 328).rows;
    expect(rows.map(({ tiles }) => tiles.length)).toEqual([1, 1]);
    expect(rows[1]!.height).toBe(246);
    expect(rows[1]!.width).toBe(61.5);
});

test("a landscape can share a row with two portraits to avoid a lone portrait", () => {
    const tiles = photos([3 / 4, 3 / 4, 16 / 9, 3 / 4, 9 / 16]);
    expect(
        postPhotoLayout(tiles, 363).rows.map(({ tiles }) =>
            tiles.map(({ id }) => id),
        ),
    ).toEqual([
        ["0", "1"],
        ["2", "3", "4"],
    ]);
});

test("a single-post grid fills the width with the feed's portrait height cap", () => {
    const tiles = photos([9 / 16]);
    expect(postPhotoLayout(tiles, 324).rows).toEqual([
        { aspectRatio: 9 / 16, height: 432, width: 324, tiles },
    ]);
});

test("similar landscapes are not forced into full-width rows", () => {
    const tiles = photos(new Array<number>(10).fill(3 / 2));
    const rows = postPhotoLayout(tiles, 358).rows;
    expect(rows.map(({ tiles }) => tiles.length)).toEqual([2, 2, 2, 2, 2]);
    expect(rows.flatMap(({ tiles }) => tiles)).toEqual(tiles);
});

test("wider photos stand alone when that fits better than a short paired row", () => {
    const tiles = photos([3 / 2, 3 / 2, 5 / 3, 5 / 3, 3 / 2, 3 / 2]);
    const rows = postPhotoLayout(tiles, 358).rows;
    expect(rows.map(({ tiles }) => tiles.length)).toEqual([2, 1, 1, 2]);
    expect(rows.flatMap(({ tiles }) => tiles)).toEqual(tiles);
});

test("row grouping adapts to the available width and accounts for gaps", () => {
    const tiles = photos([1.5, 1.5, 1.5, 1.5, 1.5, 1.5]);
    expect(
        postPhotoLayout(tiles, 299 + profilePhotoGap).rows.map(
            ({ tiles }) => tiles.length,
        ),
    ).toEqual([1, 1, 1, 1, 1, 1]);
    expect(
        postPhotoLayout(tiles, 300 + profilePhotoGap).rows.map(
            ({ tiles }) => tiles.length,
        ),
    ).toEqual([2, 2, 2]);
    expect(
        postPhotoLayout(tiles, 456).rows.map(({ tiles }) => tiles.length),
    ).toEqual([2, 2, 2]);
});

test("a panorama keeps its aspect ratio even when it cannot reach the minimum", () => {
    const tiles = photos([8, 1, 1, 1, 1]);
    const rows = postPhotoLayout(tiles, 328).rows;
    expect(rows.map(({ tiles }) => tiles.length)).toEqual([1, 2, 2]);
    expect(rows[0]!.aspectRatio).toBe(8);
});

test.each([288, 328, 358, 568])(
    "rows preserve order, fit a %ipx grid, and meet the minimum when possible",
    (width) => {
        const tiles = photos([1.5, 1.2, 1.8, 0.75, 0.6, 8, 0.05, 1, 2, 1.5]);
        const rows = postPhotoLayout(tiles, width).rows;
        expect(rows.flatMap(({ tiles }) => tiles)).toEqual(tiles);
        for (const row of rows) {
            expect(row.tiles.length).toBeLessThanOrEqual(3);
            const gaps = (row.tiles.length - 1) * profilePhotoGap;
            expect(
                row.tiles.reduce(
                    (sum, tile) => sum + tile.aspectRatio * row.height,
                    gaps,
                ),
            ).toBeCloseTo(row.width);
            expect(row.width).toBeLessThanOrEqual(width + 0.001);
            if (row.tiles.length > 1) expect(row.width).toBe(width);
            else expect(row.height).toBeLessThanOrEqual(width * 0.75);
            expect(row.height).toBeGreaterThanOrEqual(
                Math.min(100, width / row.tiles[0]!.aspectRatio),
            );
        }
    },
);

test("an empty grid has no rows", () => {
    expect(postPhotoLayout([], 328).rows).toEqual([]);
});

test("the grid waits for its width to be measured", () => {
    expect(postPhotoLayout(photos([3 / 4, 3 / 2]), 0).rows).toEqual([]);
});

test.each([1, 2, 3, 4, 5, 6, 7])(
    "a complete collection of %i posts gives each photo its own full-width row",
    (count) => {
        const tiles = photos([16 / 9, 3 / 4, 1, 9 / 16, 2, 0.3, 1.5]).slice(
            0,
            count,
        );
        const layout = postPhotoLayout(tiles, 324, undefined, count);
        expect(layout.fullWidth).toBe(true);
        expect(layout.rows).toHaveLength(count);
        expect(layout.rows.flatMap((row) => row.tiles)).toEqual(tiles);
        layout.rows.forEach((row) => {
            expect(row.width).toBe(324);
            expect(row.height).toBeCloseTo(
                Math.min(324 / row.tiles[0]!.aspectRatio, 432),
            );
        });
    },
);

test("eight posts use the adaptive grid", () => {
    const tiles = photos(new Array<number>(8).fill(1));
    const layout = postPhotoLayout(tiles, 352, undefined, 8);
    expect(layout.fullWidth).toBe(false);
    expect(layout.rows).toEqual(postPhotoLayout(tiles, 352).rows);
});

test.each([undefined, 8])(
    "two loaded photos do not imply a small collection when the total is %s",
    (postCount) => {
        const tiles = photos([16 / 9, 3 / 4]);
        const layout = postPhotoLayout(tiles, 352, undefined, postCount);
        expect(layout.fullWidth).toBe(false);
        expect(layout.rows).toHaveLength(1);
    },
);

test("the layout decision waits for photos and a measured width", () => {
    const tiles = photos([16 / 9, 3 / 4]);
    const empty = postPhotoLayout([], 352, undefined, 0);
    expect(empty.fullWidth).toBeUndefined();
    const unmeasured = postPhotoLayout(tiles, 0, empty, 2);
    expect(unmeasured.fullWidth).toBeUndefined();
    expect(postPhotoLayout(tiles, 352, unmeasured, 2).fullWidth).toBe(true);
});

test("crossing seven posts keeps the chosen layout until the next opening", () => {
    const tiles = photos(new Array<number>(8).fill(1));
    const initial = postPhotoLayout(tiles.slice(0, 7), 352, undefined, 7);
    const appended = postPhotoLayout(tiles, 352, initial, 8);
    expect(appended.fullWidth).toBe(true);
    expect(appended.rows.slice(0, 7)).toEqual(initial.rows);
    expect(appended.rows.every((row) => row.tiles.length == 1)).toBe(true);

    const reopened = postPhotoLayout(tiles, 352, undefined, 8);
    expect(reopened.fullWidth).toBe(false);
    const afterDeletion = postPhotoLayout(tiles.slice(0, 7), 352, reopened, 7);
    expect(afterDeletion.fullWidth).toBe(false);
});

test("resizing retains the full-width layout for a small collection", () => {
    const tiles = photos([16 / 9, 3 / 4]);
    const initial = postPhotoLayout(tiles, 352, undefined, 2);
    const resized = postPhotoLayout(tiles, 600, initial, 2);
    expect(resized.fullWidth).toBe(true);
    expect(resized.rows).toHaveLength(2);
    expect(resized.rows.every((row) => row.width == 600)).toBe(true);
});

test.each([1, 3, 10])(
    "appending pages preserves every row from the initial %i photos",
    (initialCount) => {
        const tiles = photos(
            Array.from(
                { length: 40 },
                (_, index) => [3 / 4, 3 / 2, 9 / 16, 1, 8][index % 5]!,
            ),
        );
        let layout = postPhotoLayout(tiles.slice(0, initialCount), 352);
        for (let count = initialCount + 10; count < 50; count += 10) {
            const loadedTiles = tiles.slice(0, count);
            const next = postPhotoLayout(loadedTiles, 352, layout);
            expect(next.rows.slice(0, layout.rows.length)).toEqual(layout.rows);
            expect(next.rows.flatMap((row) => row.tiles)).toEqual(loadedTiles);
            layout = next;
        }
    },
);

test("late dimensions do not resize existing rows or their tiles", () => {
    const tiles = photos([1, 1, 1]);
    const initial = postPhotoLayout(tiles, 352);
    const updated = tiles.map((tile) => ({ ...tile, aspectRatio: 9 / 16 }));
    const next = postPhotoLayout(
        [...updated, { id: "older", aspectRatio: 3 / 2 }],
        352,
        initial,
    );
    expect(next.rows.slice(0, initial.rows.length)).toEqual(initial.rows);
    expect(next.rows.at(-1)!.tiles).toEqual([
        { id: "older", aspectRatio: 3 / 2 },
    ]);
});

test("deleting a photo retains preceding rows and removes the deleted tile", () => {
    const tiles = photos([1, 1, 1, 1, 1, 1]);
    const initial = postPhotoLayout(tiles, 352);
    const remaining = tiles.filter((tile) => tile.id != "3");
    const next = postPhotoLayout(remaining, 352, initial);
    expect(next.rows[0]).toEqual(initial.rows[0]);
    expect(next.rows.flatMap((row) => row.tiles)).toEqual(remaining);
});

test("a replaced or reordered list uses the current photo order", () => {
    const tiles = photos([3 / 4, 3 / 2, 1, 9 / 16]);
    const initial = postPhotoLayout(tiles, 352);
    const reordered = [...tiles].reverse();
    const next = postPhotoLayout(reordered, 352, initial);
    expect(next.rows).toEqual(postPhotoLayout(reordered, 352).rows);
    expect(next.rows.flatMap((row) => row.tiles)).toEqual(reordered);
    expect(postPhotoLayout([], 352, next).rows).toEqual([]);
});

test("resizing recalculates rows to fit the new width", () => {
    const tiles = photos([3 / 2, 3 / 2, 3 / 2, 3 / 2]);
    const initial = postPhotoLayout(tiles, 352);
    const next = postPhotoLayout(tiles, 600, initial);
    expect(next.width).toBe(600);
    expect(next.rows).toEqual(postPhotoLayout(tiles, 600).rows);
    expect(next.rows).not.toEqual(initial.rows);
});
