package repo

import (
	"context"
	"testing"

	"github.com/ente/museum/internal/testutil"
)

func TestRepairCollectionSelfShares(t *testing.T) {
	_, db := setupAccessibleObjectTest(t)
	repository := &CollectionRepository{DB: db}
	ownerID := testutil.InsertUser(t, db, testutil.UserFixture{
		UserID:       1,
		Email:        "owner@example.com",
		CreationTime: 1,
	})
	shareeID := testutil.InsertUser(t, db, testutil.UserFixture{
		UserID:       2,
		Email:        "sharee@example.com",
		CreationTime: 1,
	})
	collectionID := insertObjectTestCollection(t, db, ownerID)
	insertObjectTestCollectionShare(t, db, collectionID, ownerID, ownerID)
	insertObjectTestCollectionShare(t, db, collectionID, ownerID, shareeID)
	insertObjectTestCollectionShare(t, db, collectionID, shareeID, ownerID)

	repaired, err := repository.RepairCollectionSelfShare(context.Background(), collectionID, ownerID, 200)
	if err != nil {
		t.Fatal(err)
	}
	if !repaired {
		t.Fatal("repair returned false, want true")
	}

	var ownerShareCount int
	err = db.QueryRow(
		`SELECT count(*)
		 FROM collection_shares
		 WHERE collection_id = $1 AND from_user_id = $2 AND to_user_id = $2`,
		collectionID,
		ownerID,
	).Scan(&ownerShareCount)
	if err != nil {
		t.Fatal(err)
	}
	if ownerShareCount != 0 {
		t.Fatalf("owner self-share count = %d, want 0", ownerShareCount)
	}

	var validShareDeleted bool
	err = db.QueryRow(
		`SELECT is_deleted
		 FROM collection_shares
		 WHERE collection_id = $1 AND to_user_id = $2`,
		collectionID,
		shareeID,
	).Scan(&validShareDeleted)
	if err != nil {
		t.Fatal(err)
	}
	if validShareDeleted {
		t.Fatal("valid sharee row was deleted")
	}

	var reverseShareCount int
	err = db.QueryRow(
		`SELECT count(*)
		 FROM collection_shares
		 WHERE collection_id = $1 AND from_user_id = $2 AND to_user_id = $3`,
		collectionID,
		shareeID,
		ownerID,
	).Scan(&reverseShareCount)
	if err != nil {
		t.Fatal(err)
	}
	if reverseShareCount != 1 {
		t.Fatalf("reverse share count = %d, want 1", reverseShareCount)
	}

	var collectionUpdationTime int64
	err = db.QueryRow(
		`SELECT updation_time FROM collections WHERE collection_id = $1`,
		collectionID,
	).Scan(&collectionUpdationTime)
	if err != nil {
		t.Fatal(err)
	}
	if collectionUpdationTime != 200 {
		t.Fatalf("collection updation_time = %d, want 200", collectionUpdationTime)
	}

	repaired, err = repository.RepairCollectionSelfShare(context.Background(), collectionID, ownerID, 300)
	if err != nil {
		t.Fatal(err)
	}
	if repaired {
		t.Fatal("second repair returned true, want false")
	}
}

func TestRepairCollectionSelfShareRequiresMatchingUser(t *testing.T) {
	_, db := setupAccessibleObjectTest(t)
	repository := &CollectionRepository{DB: db}
	ownerID := testutil.InsertUser(t, db, testutil.UserFixture{
		UserID:       1,
		Email:        "owner@example.com",
		CreationTime: 1,
	})
	firstCollectionID := insertObjectTestCollection(t, db, ownerID)
	secondCollectionID := insertObjectTestCollection(t, db, ownerID)
	insertObjectTestCollectionShare(t, db, firstCollectionID, ownerID, ownerID)
	insertObjectTestCollectionShare(t, db, secondCollectionID, ownerID, ownerID)

	repaired, err := repository.RepairCollectionSelfShare(context.Background(), secondCollectionID, ownerID+1, 200)
	if err != nil {
		t.Fatal(err)
	}
	if repaired {
		t.Fatal("repair with mismatched user returned true, want false")
	}
	repaired, err = repository.RepairCollectionSelfShare(context.Background(), secondCollectionID, ownerID, 200)
	if err != nil {
		t.Fatal(err)
	}
	if !repaired {
		t.Fatal("repair with matching user returned false, want true")
	}

	var firstCount, secondCount int
	err = db.QueryRow(
		`SELECT count(*) FROM collection_shares WHERE collection_id = $1 AND to_user_id = $2`,
		firstCollectionID,
		ownerID,
	).Scan(&firstCount)
	if err != nil {
		t.Fatal(err)
	}
	err = db.QueryRow(
		`SELECT count(*) FROM collection_shares WHERE collection_id = $1 AND to_user_id = $2`,
		secondCollectionID,
		ownerID,
	).Scan(&secondCount)
	if err != nil {
		t.Fatal(err)
	}
	if firstCount != 1 || secondCount != 0 {
		t.Fatalf("self-share counts: first=%d second=%d, want 1/0", firstCount, secondCount)
	}
}
