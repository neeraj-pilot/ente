package repo

import (
	"database/sql"
	"errors"
	"testing"

	"github.com/ente/museum/ente"
)

func TestInitializeFileAppBackfillsOnlyLockerCandidates(t *testing.T) {
	_, db, userID := setupCollectionMembershipTest(t)
	usageRepo := &UsageRepository{DB: db}
	photosFileID := insertObjectTestFile(t, db, userID)
	lockerFileID := insertObjectTestFile(t, db, userID)
	lockerCollectionID := insertObjectTestCollection(t, db, userID)
	if _, err := db.Exec(`UPDATE collections SET app = 'locker', is_deleted = TRUE
		WHERE collection_id = $1`, lockerCollectionID); err != nil {
		t.Fatal(err)
	}
	linkObjectTestFileToCollection(t, db, lockerCollectionID, lockerFileID, userID)
	if _, err := db.Exec(`UPDATE collection_files SET is_deleted = TRUE
		WHERE collection_id = $1 AND file_id = $2`, lockerCollectionID, lockerFileID); err != nil {
		t.Fatal(err)
	}

	initialized, err := usageRepo.InitializeFileApp(t.Context(), userID)
	if err != nil || !initialized {
		t.Fatalf("InitializeFileApp() = (%t, %v), want (true, nil)", initialized, err)
	}

	var ready bool
	if err := db.QueryRow(`SELECT file_app_ready FROM usage WHERE user_id = $1`, userID).Scan(&ready); err != nil {
		t.Fatal(err)
	}
	if !ready {
		t.Fatal("file app provenance is not ready")
	}
	assertFileApp(t, db, lockerFileID, sql.NullString{String: string(ente.Locker), Valid: true})
	assertFileApp(t, db, photosFileID, sql.NullString{})
}

func TestInitializeFileAppRejectsAmbiguousLockerCandidates(t *testing.T) {
	tests := []struct {
		name  string
		setup func(t *testing.T, db *sql.DB, userID int64, fileID int64, lockerCollectionID int64)
	}{
		{
			name: "explicit Photos app",
			setup: func(t *testing.T, db *sql.DB, _ int64, fileID int64, _ int64) {
				if _, err := db.Exec(`UPDATE files SET app = 'photos' WHERE file_id = $1`, fileID); err != nil {
					t.Fatal(err)
				}
			},
		},
		{
			name: "NULL file with Photos membership history",
			setup: func(t *testing.T, db *sql.DB, userID int64, fileID int64, _ int64) {
				photosCollectionID := insertObjectTestCollection(t, db, userID)
				linkObjectTestFileToCollection(t, db, photosCollectionID, fileID, userID)
			},
		},
		{
			name: "explicit Locker app with Photos membership",
			setup: func(t *testing.T, db *sql.DB, userID int64, fileID int64, lockerCollectionID int64) {
				if _, err := db.Exec(`UPDATE files SET app = 'locker' WHERE file_id = $1`, fileID); err != nil {
					t.Fatal(err)
				}
				if _, err := db.Exec(`DELETE FROM collection_files
					WHERE collection_id = $1 AND file_id = $2`, lockerCollectionID, fileID); err != nil {
					t.Fatal(err)
				}
				photosCollectionID := insertObjectTestCollection(t, db, userID)
				linkObjectTestFileToCollection(t, db, photosCollectionID, fileID, userID)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, db, userID := setupCollectionMembershipTest(t)
			fileID := insertObjectTestFile(t, db, userID)
			lockerCollectionID := insertObjectTestCollection(t, db, userID)
			if _, err := db.Exec(`UPDATE collections SET app = 'locker' WHERE collection_id = $1`, lockerCollectionID); err != nil {
				t.Fatal(err)
			}
			linkObjectTestFileToCollection(t, db, lockerCollectionID, fileID, userID)
			tt.setup(t, db, userID, fileID, lockerCollectionID)

			initialized, err := (&UsageRepository{DB: db}).InitializeFileApp(t.Context(), userID)
			if initialized || !errors.Is(err, ErrFileAppIneligible) {
				t.Fatalf("InitializeFileApp() = (%t, %v), want ineligible", initialized, err)
			}
			var ready bool
			if err := db.QueryRow(`SELECT file_app_ready FROM usage WHERE user_id = $1`, userID).Scan(&ready); err != nil {
				t.Fatal(err)
			}
			if ready {
				t.Fatal("ineligible user was marked ready")
			}
		})
	}
}

func TestInitializeFileAppRollsBackStaleBackfill(t *testing.T) {
	repository, db, userID := setupCollectionMembershipTest(t)
	usageRepo := &UsageRepository{DB: db}
	fileID := insertObjectTestFile(t, db, userID)
	photosCollectionID := insertObjectTestCollection(t, db, userID)
	lockerCollectionID := insertObjectTestCollection(t, db, userID)
	linkObjectTestFileToCollection(t, db, photosCollectionID, fileID, userID)
	if _, err := db.Exec(`UPDATE collections SET app = 'locker' WHERE collection_id = $1`, lockerCollectionID); err != nil {
		t.Fatal(err)
	}
	snapshot, err := usageRepo.readFileAppInitSnapshot(t.Context(), userID)
	if err != nil || snapshot.ineligibilityReason != "" {
		t.Fatalf("snapshot = %+v, error = %v", snapshot, err)
	}
	if err := repository.AddFiles(t.Context(), lockerCollectionID, userID,
		[]ente.CollectionFileItem{collectionMembershipTestItem(fileID)}, userID); err != nil {
		t.Fatal(err)
	}
	initialized, err := usageRepo.initializeFileAppAtVersion(t.Context(), userID, snapshot.version)
	if err != nil || initialized {
		t.Fatalf("stale initialization = (%t, %v), want (false, nil)", initialized, err)
	}
	assertFileApp(t, db, fileID, sql.NullString{})
	assertFileAppReadiness(t, db, userID, false, 1)
	initialized, err = usageRepo.InitializeFileApp(t.Context(), userID)
	if initialized || !errors.Is(err, ErrFileAppIneligible) {
		t.Fatalf("retry = (%t, %v), want ineligible", initialized, err)
	}
}

func assertFileApp(t *testing.T, db *sql.DB, fileID int64, want sql.NullString) {
	t.Helper()
	var got sql.NullString
	if err := db.QueryRow(`SELECT app FROM files WHERE file_id = $1`, fileID).Scan(&got); err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Fatalf("file app = %v, want %v", got, want)
	}
}
