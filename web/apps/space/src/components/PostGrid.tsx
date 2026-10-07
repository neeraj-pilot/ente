import { Box } from "@mui/material";
import React from "react";
import { spacePostFrameAspectRatio } from "utils/post-photos";
import { postPhotoLayout, profilePhotoGap } from "utils/profile-photo-layout";

interface PostGridItem {
    id: string;
    width?: number;
    height?: number;
    frameAspectRatio?: number;
}

export const SpacePostGrid = <Item extends PostGridItem>({
    items,
    postCount,
    renderTile,
}: {
    items: Item[];
    postCount?: number;
    renderTile: (
        item: Item,
        index: number,
        flexGrow: number,
    ) => React.ReactNode;
}) => {
    const [width, setWidth] = React.useState(0);
    const [previousLayout, setLayout] = React.useState(() => ({
        ...postPhotoLayout([], 0),
        items,
        postCount,
    }));
    const gridRef = React.useRef<HTMLDivElement | null>(null);
    React.useLayoutEffect(() => {
        const grid = gridRef.current!;
        const observer = new ResizeObserver(([entry]) =>
            setWidth(entry!.contentRect.width),
        );
        setWidth(grid.getBoundingClientRect().width);
        observer.observe(grid);
        return () => observer.disconnect();
    }, []);

    const layout =
        previousLayout.items == items &&
        previousLayout.width == width &&
        previousLayout.postCount == postCount
            ? previousLayout
            : {
                  ...postPhotoLayout(
                      items.map((item) => ({
                          id: item.id,
                          aspectRatio:
                              item.frameAspectRatio ??
                              spacePostFrameAspectRatio([item]),
                      })),
                      width,
                      previousLayout,
                      postCount,
                  ),
                  items,
                  postCount,
              };
    if (layout != previousLayout) setLayout(layout);

    const itemsByID = new Map(
        items.map((item, index) => [item.id, { item, index }]),
    );

    return (
        <Box
            ref={gridRef}
            sx={{
                display: "flex",
                flexDirection: "column",
                gap: `${profilePhotoGap}px`,
            }}
        >
            {layout.rows.map((row) => (
                <Box
                    key={row.tiles[0]!.id}
                    sx={{
                        display: "flex",
                        flexShrink: 0,
                        gap: `${profilePhotoGap}px`,
                        height: row.height,
                        width: row.width,
                    }}
                >
                    {row.tiles.map(({ id, aspectRatio }) => {
                        const { item, index } = itemsByID.get(id)!;
                        return renderTile(
                            item,
                            index,
                            aspectRatio / row.aspectRatio,
                        );
                    })}
                </Box>
            ))}
        </Box>
    );
};
