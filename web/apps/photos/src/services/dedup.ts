import { ensureLocalUser } from "ente-accounts/services/user";
import { assertionFailed } from "ente-base/assert";
import { newID } from "ente-base/id";
import type { EnteFile } from "ente-media/file";
import {
    FilePublicMagicMetadataData,
    metadataHash,
} from "ente-media/file-metadata";
import {
    addToCollection,
    createCollectionNameByID,
    moveToTrash,
    savedNormalCollections,
} from "ente-new/photos/services/collection";
import { savedCollectionFiles } from "ente-new/photos/services/photos-fdb";
import { findDuplicates, planDuplicateRemoval } from "ente-photos-wasm";

export interface DuplicateGroup {
    id: string;
    items: {
        file: EnteFile;
        collectionIDs: Set<number>;
        collectionName: string;
    }[];
    itemSize: number;
    prunableCount: number;
    prunableSize: number;
    isSelected: boolean;
}

export const deduceDuplicates = async () => {
    const userID = ensureLocalUser().id;

    const normalCollections = await savedNormalCollections();
    const collectionNameByID = createCollectionNameByID(normalCollections);
    const collectionFiles = await savedCollectionFiles();
    const { groups } = await findDuplicates({
        userId: userID,
        collections: normalCollections.map(({ id, owner }) => ({
            id,
            ownerId: owner.id,
        })),
        files: collectionFiles.map((file) => ({
            id: file.id,
            collectionId: file.collectionID,
            ownerId: file.ownerID,
            hash: metadataHash(file.metadata),
            size: file.info?.fileSize,
        })),
    });

    const duplicateGroups: DuplicateGroup[] = [];

    for (const group of groups) {
        const items = group.items
            .map(({ fileIndex, collectionIds }) => {
                const file = collectionFiles[fileIndex]!;
                const collectionName = collectionNameByID.get(
                    file.collectionID,
                );
                if (!collectionName) {
                    assertionFailed();
                    return undefined;
                }

                return {
                    file,
                    collectionIDs: new Set(collectionIds),
                    collectionName,
                };
            })
            .filter((item) => !!item);
        if (items.length < 2) continue;

        items.sort((a, b) => a.collectionName.localeCompare(b.collectionName));

        duplicateGroups.push({
            id: newID("dg_"),
            items,
            itemSize: group.itemSize,
            prunableCount: items.length - 1,
            prunableSize: group.itemSize * (items.length - 1),
            isSelected: true,
        });
    }

    return duplicateGroups;
};

export const removeSelectedDuplicateGroups = async (
    duplicateGroups: DuplicateGroup[],
    onProgress: (progress: number) => void,
    onRemoteFilesPull: () => Promise<void>,
) => {
    const selectedDuplicateGroups = duplicateGroups.filter((g) => g.isSelected);

    const filesByID = new Map(
        selectedDuplicateGroups.flatMap((group) =>
            group.items.map(({ file }) => [file.id, file] as const),
        ),
    );
    const plan = await planDuplicateRemoval({
        groups: selectedDuplicateGroups.map((group) =>
            group.items.map(({ file, collectionIDs }) => ({
                fileId: file.id,
                collectionIds: [...collectionIDs],
                caption: FilePublicMagicMetadataData.shape.caption.parse(
                    file.pubMagicMetadata?.data.caption,
                ),
                editedName: file.pubMagicMetadata?.data.editedName,
                editedTime: file.pubMagicMetadata?.data.editedTime,
            })),
        ),
    });

    let np = 0;
    const ntotal =
        plan.additions.length + (plan.trashIds.length ? 1 : 0) + /* sync */ 1;
    const tickProgress = () => onProgress((++np / ntotal) * 100);

    const collections = await savedNormalCollections();
    const collectionsByID = new Map(collections.map((c) => [c.id, c]));
    for (const { collectionId, fileIds } of plan.additions) {
        await addToCollection(
            collectionsByID.get(collectionId)!,
            fileIds.map((id) => filesByID.get(id)!),
        );
        tickProgress();
    }

    if (plan.trashIds.length) {
        await moveToTrash(plan.trashIds.map((id) => filesByID.get(id)!));
        tickProgress();
    }

    await onRemoteFilesPull();
    tickProgress();

    return new Set(selectedDuplicateGroups.map((g) => g.id));
};
