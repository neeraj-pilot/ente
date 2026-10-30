import type { EnteFile } from "ente-media/file";
import { beforeEach, describe, expect, test, vi } from "vitest";
import {
    deduceDuplicates,
    removeSelectedDuplicateGroups,
    type DuplicateGroup,
} from "../src/services/dedup";

const {
    addToCollection,
    moveToTrash,
    savedNormalCollections,
    savedCollectionFiles,
} = vi.hoisted(() => ({
    addToCollection: vi.fn(),
    moveToTrash: vi.fn(),
    savedNormalCollections: vi.fn(),
    savedCollectionFiles: vi.fn(),
}));
vi.mock("ente-new/photos/services/collection", () => ({
    addToCollection,
    moveToTrash,
    savedNormalCollections,
    createCollectionNameByID: (collections: { id: number; name: string }[]) =>
        new Map(collections.map(({ id, name }) => [id, name])),
}));
vi.mock("ente-new/photos/services/photos-fdb", () => ({
    savedCollectionFiles,
}));
vi.mock("ente-accounts/services/user", () => ({
    ensureLocalUser: () => ({ id: 1 }),
}));

const file = (
    id: number,
    collectionID: number,
    hash: string | undefined,
    size: number | undefined = 100,
) =>
    ({
        id,
        collectionID,
        ownerID: 1,
        metadata: { fileType: 0, hash },
        info: { fileSize: size },
    }) as EnteFile;

describe("deduceDuplicates through Rust WASM", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        savedNormalCollections.mockResolvedValue([
            { id: 11, owner: { id: 1 }, name: "B" },
            { id: 12, owner: { id: 1 }, name: "A" },
            { id: 13, owner: { id: 2 }, name: "Shared" },
        ]);
    });

    test("groups owned files once across albums, excluding shared files and hidden albums", async () => {
        const first = file(1, 11, "hash");
        const second = file(2, 12, "hash");
        savedCollectionFiles.mockResolvedValue([
            first,
            { ...first, collectionID: 12 },
            second,
            { ...file(3, 11, "hash"), ownerID: 2 },
            file(4, 13, "hash"),
            file(5, 99, "hash"),
            file(6, 11, undefined),
            file(7, 11, "unique"),
        ]);
        const groups = await deduceDuplicates();
        expect(groups).toEqual([
            {
                id: expect.any(String) as string,
                items: [
                    {
                        file: second,
                        collectionIDs: new Set([12]),
                        collectionName: "A",
                    },
                    {
                        file: first,
                        collectionIDs: new Set([11, 12]),
                        collectionName: "B",
                    },
                ],
                itemSize: 100,
                prunableCount: 1,
                prunableSize: 100,
                isSelected: true,
            },
        ]);
        await removeSelectedDuplicateGroups(groups, vi.fn(), vi.fn());
        expect(addToCollection).toHaveBeenCalledExactlyOnceWith(
            expect.objectContaining({ id: 11 }),
            [second],
        );
        expect(moveToTrash).toHaveBeenCalledExactlyOnceWith([first]);
    });

    test("matches legacy Live Photo hashes despite different ZIP sizes and skips groups without sizes", async () => {
        const legacy = file(1, 11, undefined, 0);
        legacy.metadata = {
            ...legacy.metadata,
            fileType: 2,
            imageHash: "image",
            videoHash: "video",
        };
        savedCollectionFiles.mockResolvedValue([
            legacy,
            file(2, 12, "image:video", 150),
            file(3, 11, "image:video", 200),
            file(4, 11, "unknown-size", 0),
            { ...file(5, 11, "unknown-size"), info: undefined },
        ]);
        const groups = await deduceDuplicates();
        expect(groups).toHaveLength(1);
        expect(groups[0]).toMatchObject({
            itemSize: 150,
            prunableCount: 2,
            prunableSize: 300,
        });
        expect(groups[0]!.items.map(({ file }) => file.id)).toEqual([2, 1, 3]);
    });

    test("ignores a single file linked to multiple albums and empty libraries", async () => {
        savedCollectionFiles.mockResolvedValue([
            file(1, 11, "hash"),
            file(1, 12, "hash"),
            file(1, 12, "hash"),
        ]);
        expect(await deduceDuplicates()).toEqual([]);
        savedCollectionFiles.mockResolvedValue([]);
        expect(await deduceDuplicates()).toEqual([]);
    });
});

const duplicateGroup = (): DuplicateGroup => ({
    id: "group-1",
    items: [
        {
            file: { id: 1 } as EnteFile,
            collectionIDs: new Set([11, 12]),
            collectionName: "A",
        },
        {
            file: {
                id: 2,
                pubMagicMetadata: { data: { caption: "Keep this copy" } },
            } as EnteFile,
            collectionIDs: new Set([12]),
            collectionName: "B",
        },
        {
            file: { id: 3 } as EnteFile,
            collectionIDs: new Set([13]),
            collectionName: "C",
        },
    ],
    itemSize: 100,
    prunableCount: 2,
    prunableSize: 200,
    isSelected: true,
});

const collections = [{ id: 11 }, { id: 12 }, { id: 13 }];

describe("removeSelectedDuplicateGroups", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        savedNormalCollections.mockResolvedValue(collections);
    });

    test.each([123, 0])(
        "normalizes legacy cached numeric caption %s before planning in Rust",
        async (caption) => {
            const group = duplicateGroup();
            const retained = group.items[1]!.file;
            Object.assign(retained.pubMagicMetadata!.data, { caption });
            const refresh = vi.fn();

            await expect(
                removeSelectedDuplicateGroups([group], vi.fn(), refresh),
            ).resolves.toEqual(new Set([group.id]));
            expect(addToCollection.mock.calls).toEqual([
                [collections[0], [retained]],
                [collections[2], [retained]],
            ]);
            expect(moveToTrash).toHaveBeenCalledExactlyOnceWith([
                group.items[0]!.file,
                group.items[2]!.file,
            ]);
            expect(refresh).toHaveBeenCalledOnce();
        },
    );

    test.each([
        {
            label: "caption before edits",
            metadata: [{ editedName: "renamed" }, { caption: "caption" }, {}],
            retainedId: 2,
        },
        {
            label: "renamed copy",
            metadata: [{}, {}, { editedName: "renamed" }],
            retainedId: 3,
        },
        {
            label: "edited timestamp",
            metadata: [{}, { editedTime: 123 }, {}],
            retainedId: 2,
        },
        {
            label: "first of equal captions",
            metadata: [{}, { caption: "one" }, { caption: "two" }],
            retainedId: 2,
        },
        {
            label: "first of equal edits",
            metadata: [{}, { editedName: "one" }, { editedTime: 123 }],
            retainedId: 2,
        },
        {
            label: "empty caption and null caption",
            metadata: [{}, { caption: "" }, { caption: null }],
            retainedId: 1,
        },
        {
            label: "empty name overrides edited timestamp",
            metadata: [{}, { editedName: "", editedTime: 123 }, {}],
            retainedId: 1,
        },
    ])(
        "retains the $label through Rust WASM",
        async ({ metadata, retainedId }) => {
            const group = duplicateGroup();
            group.items.forEach(({ file }, index) => {
                Object.assign(file, {
                    pubMagicMetadata: { data: metadata[index] },
                });
            });
            await removeSelectedDuplicateGroups([group], vi.fn(), vi.fn());
            expect(moveToTrash).toHaveBeenCalledExactlyOnceWith(
                group.items
                    .filter(({ file }) => file.id !== retainedId)
                    .map(({ file }) => file),
            );
        },
    );

    test("batches retained copies from different groups into each missing album", async () => {
        const first = duplicateGroup();
        const second = duplicateGroup();
        second.id = "group-2";
        for (const { file } of second.items) file.id += 3;
        const refresh = vi.fn();
        await expect(
            removeSelectedDuplicateGroups([first, second], vi.fn(), refresh),
        ).resolves.toEqual(new Set([first.id, second.id]));
        const retained = [first.items[1]!.file, second.items[1]!.file];
        expect(addToCollection.mock.calls).toEqual([
            [collections[0], retained],
            [collections[2], retained],
        ]);
        expect(moveToTrash).toHaveBeenCalledExactlyOnceWith([
            first.items[0]!.file,
            first.items[2]!.file,
            second.items[0]!.file,
            second.items[2]!.file,
        ]);
        expect(refresh).toHaveBeenCalledOnce();
    });

    test("rejects invalid planner input before modifying albums or trash", async () => {
        const group = duplicateGroup();
        group.items[1]!.file.id = Number.MAX_SAFE_INTEGER + 1;
        const refresh = vi.fn();
        await expect(
            removeSelectedDuplicateGroups([group], vi.fn(), refresh),
        ).rejects.toBeInstanceOf(Error);
        expect(addToCollection).not.toHaveBeenCalled();
        expect(moveToTrash).not.toHaveBeenCalled();
        expect(refresh).not.toHaveBeenCalled();
    });

    test("preserves album membership before trashing selected duplicates and awaits gallery refresh", async () => {
        const group = duplicateGroup();
        const untouched = {
            ...duplicateGroup(),
            id: "unselected",
            isSelected: false,
        };
        const progress = vi.fn();
        let finishRefresh!: () => void;
        const refresh = vi.fn(
            () =>
                new Promise<void>((resolve) => {
                    finishRefresh = resolve;
                }),
        );
        const removal = removeSelectedDuplicateGroups(
            [group, untouched],
            progress,
            refresh,
        );
        await vi.waitFor(() => expect(refresh).toHaveBeenCalledOnce());
        expect(addToCollection.mock.calls).toEqual([
            [collections[0], [group.items[1]!.file]],
            [collections[2], [group.items[1]!.file]],
        ]);
        expect(moveToTrash).toHaveBeenCalledExactlyOnceWith([
            group.items[0]!.file,
            group.items[2]!.file,
        ]);
        expect(addToCollection.mock.invocationCallOrder[1]).toBeLessThan(
            moveToTrash.mock.invocationCallOrder[0]!,
        );
        expect(moveToTrash.mock.invocationCallOrder[0]).toBeLessThan(
            refresh.mock.invocationCallOrder[0]!,
        );
        expect(progress.mock.calls).toEqual([[25], [50], [75]]);
        finishRefresh();
        await expect(removal).resolves.toEqual(new Set([group.id]));
        expect(progress).toHaveBeenLastCalledWith(100);
    });

    test("does not trash files when preserving album membership fails", async () => {
        const failure = new Error("Add failed");
        addToCollection.mockRejectedValue(failure);
        const refresh = vi.fn();
        await expect(
            removeSelectedDuplicateGroups([duplicateGroup()], vi.fn(), refresh),
        ).rejects.toBe(failure);
        expect(moveToTrash).not.toHaveBeenCalled();
        expect(refresh).not.toHaveBeenCalled();
    });

    test("does not trash any files if a later album addition fails", async () => {
        const failure = new Error("Second album failed");
        addToCollection
            .mockResolvedValueOnce(undefined)
            .mockRejectedValueOnce(failure);
        const refresh = vi.fn();
        await expect(
            removeSelectedDuplicateGroups([duplicateGroup()], vi.fn(), refresh),
        ).rejects.toBe(failure);
        expect(addToCollection).toHaveBeenCalledTimes(2);
        expect(moveToTrash).not.toHaveBeenCalled();
        expect(refresh).not.toHaveBeenCalled();
    });

    test("does not refresh when trashing fails", async () => {
        const failure = new Error("Trash failed");
        moveToTrash.mockRejectedValue(failure);
        const refresh = vi.fn();
        await expect(
            removeSelectedDuplicateGroups([duplicateGroup()], vi.fn(), refresh),
        ).rejects.toBe(failure);
        expect(refresh).not.toHaveBeenCalled();
    });

    test("does not report completion when gallery refresh fails", async () => {
        const failure = new Error("Refresh failed");
        const refresh = vi.fn().mockRejectedValue(failure);
        const progress = vi.fn();
        await expect(
            removeSelectedDuplicateGroups(
                [duplicateGroup()],
                progress,
                refresh,
            ),
        ).rejects.toBe(failure);
        expect(progress).not.toHaveBeenCalledWith(100);
    });

    test("only refreshes when no groups are selected", async () => {
        const refresh = vi.fn();
        await expect(
            removeSelectedDuplicateGroups(
                [{ ...duplicateGroup(), isSelected: false }],
                vi.fn(),
                refresh,
            ),
        ).resolves.toEqual(new Set());
        expect(addToCollection).not.toHaveBeenCalled();
        expect(moveToTrash).not.toHaveBeenCalled();
        expect(refresh).toHaveBeenCalledOnce();
    });
});
