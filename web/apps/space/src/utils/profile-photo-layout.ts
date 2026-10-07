import { minimumPostPhotoFrameAspectRatio } from "../styles/tiles";

export const profilePhotoGap = 6;
const profilePhotoMinRowHeight = 100;

interface PhotoRow<Tile> {
    aspectRatio: number;
    height: number;
    width: number;
    tiles: Tile[];
}

interface PostPhotoTile {
    id: string;
    aspectRatio: number;
}

interface PostPhotoLayout {
    width: number;
    fullWidth?: boolean;
    rows: PhotoRow<PostPhotoTile>[];
}

export const postPhotoLayout = (
    tiles: PostPhotoTile[],
    width: number,
    previous?: PostPhotoLayout,
    postCount?: number,
): PostPhotoLayout => {
    const fullWidth =
        previous?.fullWidth ??
        (width > 0 && tiles.length > 0
            ? postCount != undefined && Math.max(postCount, tiles.length) <= 7
            : undefined);
    const rows: PhotoRow<PostPhotoTile>[] = [];
    let retainedCount = 0;
    if (previous?.width == width) {
        for (const row of previous.rows) {
            if (
                !row.tiles.every(
                    (tile, index) =>
                        tiles[retainedCount + index]?.id == tile.id,
                )
            )
                break;
            rows.push(row);
            retainedCount += row.tiles.length;
        }
    }
    const remaining = tiles.slice(retainedCount);
    rows.push(
        ...(fullWidth
            ? remaining.flatMap((tile) => profilePhotoRows([tile], width))
            : profilePhotoRows(remaining, width)),
    );
    return { width, fullWidth, rows };
};

const profilePhotoRows = <Tile extends { aspectRatio: number }>(
    tiles: Tile[],
    width: number,
) => {
    const rows: PhotoRow<Tile>[] = [];
    if (width <= 0) return rows;

    const targetRowHeight = width / 2;
    const costs = new Array<number>(tiles.length + 1).fill(Infinity);
    const rowSizes = new Array<number>(tiles.length);
    costs[tiles.length] = 0;

    for (let index = tiles.length - 1; index >= 0; index--) {
        let aspectRatio = 0;
        for (
            let rowSize = 1;
            rowSize <= 3 && index + rowSize <= tiles.length;
            rowSize++
        ) {
            aspectRatio += tiles[index + rowSize - 1]!.aspectRatio;
            const height =
                (width - (rowSize - 1) * profilePhotoGap) / aspectRatio;
            if (rowSize > 1 && height < profilePhotoMinRowHeight) continue;

            const cost =
                (height / targetRowHeight - 1) ** 2 + costs[index + rowSize]!;
            if (cost <= costs[index]!) {
                costs[index] = cost;
                rowSizes[index] = rowSize;
            }
        }
    }

    for (let index = 0; index < tiles.length; ) {
        const rowSize = rowSizes[index]!;
        const rowTiles = tiles.slice(index, index + rowSize);
        const aspectRatio = rowTiles.reduce(
            (sum, tile) => sum + tile.aspectRatio,
            0,
        );
        const gaps = (rowSize - 1) * profilePhotoGap;
        let height = (width - gaps) / aspectRatio;
        if (rowSize == 1)
            height = Math.min(
                height,
                tiles.length == 1
                    ? width / minimumPostPhotoFrameAspectRatio
                    : width * 0.75,
            );
        rows.push({
            aspectRatio,
            height,
            width:
                rowSize > 1 || tiles.length == 1 ? width : height * aspectRatio,
            tiles: rowTiles,
        });
        index += rowSize;
    }

    return rows;
};
