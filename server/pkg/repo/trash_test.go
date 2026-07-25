package repo

import (
	"testing"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/internal/testutil"
	"github.com/ente/museum/pkg/repo/public"
)

func TestTrashFilesOnlyTransitionsRequestedItems(t *testing.T) {
	_, db := setupAccessibleObjectTest(t)
	ownerID := testutil.InsertUser(t, db, testutil.UserFixture{
		UserID:       1,
		Email:        "trash-owner@ente.com",
		CreationTime: 1,
	})
	collectionID := insertObjectTestCollection(t, db, ownerID)
	requestedFileID := insertObjectTestFile(t, db, ownerID)
	untouchedFileID := insertObjectTestFile(t, db, ownerID)
	linkObjectTestFileToCollection(t, db, collectionID, requestedFileID, ownerID)
	linkObjectTestFileToCollection(t, db, collectionID, untouchedFileID, ownerID)

	repository := &TrashRepository{
		DB:           db,
		FileLinkRepo: public.NewFileLinkRepo(db),
	}
	err := repository.TrashFiles(ownerID, ente.TrashRequest{
		OwnerID: ownerID,
		TrashItems: []ente.TrashItemRequest{{
			FileID:       requestedFileID,
			CollectionID: collectionID,
		}},
	})
	if err != nil {
		t.Fatalf("TrashFiles() error = %v", err)
	}

	var requestedDeleted, untouchedDeleted bool
	err = db.QueryRow(
		`SELECT requested.is_deleted, untouched.is_deleted
		 FROM collection_files AS requested
		 JOIN collection_files AS untouched ON untouched.collection_id = requested.collection_id
		 WHERE requested.collection_id = $1
		   AND requested.file_id = $2
		   AND untouched.file_id = $3`,
		collectionID,
		requestedFileID,
		untouchedFileID,
	).Scan(&requestedDeleted, &untouchedDeleted)
	if err != nil {
		t.Fatalf("failed to read collection file states: %v", err)
	}
	if !requestedDeleted || untouchedDeleted {
		t.Fatalf("unexpected collection file states: requested deleted=%t, untouched deleted=%t", requestedDeleted, untouchedDeleted)
	}

	var trashCount, trashedFileID int64
	if err := db.QueryRow(`SELECT COUNT(*), COALESCE(MAX(file_id), 0) FROM trash`).Scan(&trashCount, &trashedFileID); err != nil {
		t.Fatalf("failed to read trash state: %v", err)
	}
	if trashCount != 1 || trashedFileID != requestedFileID {
		t.Fatalf("unexpected trash state: count=%d file=%d, want count=1 file=%d", trashCount, trashedFileID, requestedFileID)
	}
}
