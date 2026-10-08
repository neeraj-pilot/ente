package repo

import (
	"context"
	"database/sql"
	"strings"
	"testing"
	"time"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/internal/testutil"
	"github.com/ente/museum/pkg/repo/public"
)

func TestMembershipWritesMaintainFileAppReadiness(t *testing.T) {
	for _, operation := range []string{"add", "move", "restore"} {
		for _, tt := range []struct {
			name       string
			app        ente.App
			fileApp    string
			ready      bool
			wantReady  bool
			invalidate bool
		}{
			{"unready NULL Photos", ente.Photos, "", false, false, true},
			{"unready explicit Photos", ente.Photos, "photos", false, false, true},
			{"ready NULL Photos", ente.Photos, "", true, true, false},
			{"ready NULL Locker", ente.Locker, "", true, false, true},
			{"ready Photos into Locker", ente.Locker, "photos", true, false, true},
			{"ready Locker into Photos", ente.Photos, "locker", true, false, true},
			{"ready explicit Locker", ente.Locker, "locker", true, true, false},
		} {
			t.Run(operation+"/"+tt.name, func(t *testing.T) {
				f := setupFileAppMembershipTest(t, operation)
				if _, err := f.db.Exec(`UPDATE collections SET app = $1`, tt.app); err != nil {
					t.Fatal(err)
				}
				if _, err := f.db.Exec(`UPDATE files SET app = NULLIF($1, '')::app WHERE file_id = $2`, tt.fileApp, f.fileID); err != nil {
					t.Fatal(err)
				}
				if _, err := f.db.Exec(`UPDATE usage SET file_app_ready = $1 WHERE user_id = $2`, tt.ready, f.userID); err != nil {
					t.Fatal(err)
				}
				if err := f.write(t.Context()); err != nil {
					t.Fatal(err)
				}
				var version int64
				if tt.invalidate {
					version++
				}
				if operation == "restore" {
					version++ // Restoring an inactive file also changes the file count.
				}
				assertFileAppReadiness(t, f.db, f.userID, tt.wantReady, version)
			})
		}
	}
}

func TestMembershipWritesLockUsageAfterCollections(t *testing.T) {
	for _, operation := range []string{"add", "move", "restore"} {
		for _, concurrent := range []string{"trash", "create"} {
			t.Run(operation+"/"+concurrent, func(t *testing.T) {
				f := setupFileAppMembershipTest(t, operation)
				trashFileID := insertObjectTestFile(t, f.db, f.userID)
				linkObjectTestFileToCollection(t, f.db, f.collectionID, trashFileID, f.userID)
				ctx, cancel := context.WithTimeout(t.Context(), 10*time.Second)
				defer cancel()
				blocker, err := f.db.BeginTx(ctx, nil)
				if err != nil {
					t.Fatal(err)
				}
				defer blocker.Rollback()
				var blockerPID int
				if err := blocker.QueryRowContext(ctx, `SELECT pg_backend_pid()`).Scan(&blockerPID); err != nil {
					t.Fatal(err)
				}
				if _, err := blocker.ExecContext(ctx, `UPDATE collection_files SET action = 'REMOVE'
				WHERE collection_id = $1 AND file_id = $2`, f.collectionID, f.fileID); err != nil {
					t.Fatal(err)
				}
				result := make(chan error, 1)
				go func() { result <- f.write(ctx) }()
				waitForFileAppWriter(t, ctx, f.db, blockerPID)
				// These writers take collection before usage and must finish while
				// the membership write is parked, before it acquires usage.
				if concurrent == "trash" {
					err = f.repo.TrashRepo.TrashFiles(ctx, f.userID, ente.TrashRequest{
						TrashItems: []ente.TrashItemRequest{{FileID: trashFileID, CollectionID: f.collectionID}},
					})
				} else {
					_, err = (&FileRepository{DB: f.db}).CreateMetaFile(ente.MetaFile{
						OwnerID: f.userID, CollectionID: f.collectionID,
						EncryptedKey: "key", KeyDecryptionNonce: "nonce",
						Metadata:     ente.FileAttributes{EncryptedData: "metadata", DecryptionHeader: "header"},
						UpdationTime: 1,
					}, f.userID, ente.Photos)
				}
				if err != nil {
					t.Fatal(err)
				}
				if err := blocker.Commit(); err != nil {
					t.Fatal(err)
				}
				if err := <-result; err != nil {
					t.Fatal(err)
				}
			})
		}
	}
}

func TestReadyMatchingMembershipWritesDoNotLockUsage(t *testing.T) {
	// Restore still needs the usage lock to update its file count.
	for _, operation := range []string{"add", "move"} {
		t.Run(operation, func(t *testing.T) {
			f := setupFileAppMembershipTest(t, operation)
			if _, err := f.db.Exec(`UPDATE usage SET file_app_ready = TRUE WHERE user_id = $1`, f.userID); err != nil {
				t.Fatal(err)
			}
			ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
			defer cancel()
			blocker, err := f.db.BeginTx(ctx, nil)
			if err != nil {
				t.Fatal(err)
			}
			defer blocker.Rollback()
			if _, err := blocker.ExecContext(ctx, `SELECT user_id FROM usage WHERE user_id = $1 FOR UPDATE`, f.userID); err != nil {
				t.Fatal(err)
			}
			if err := f.write(ctx); err != nil {
				t.Fatal(err)
			}
			assertFileAppReadiness(t, f.db, f.userID, true, 0)
		})
	}
}

func TestAddFilesInvalidatesConcurrentFileAppPublication(t *testing.T) {
	repository, db, userID := setupCollectionMembershipTest(t)
	photosCollectionID := insertObjectTestCollection(t, db, userID)
	lockerCollectionID := insertObjectTestCollection(t, db, userID)
	fileID := insertObjectTestFile(t, db, userID)
	linkObjectTestFileToCollection(t, db, photosCollectionID, fileID, userID)
	if _, err := db.Exec(`UPDATE collections SET app = 'locker' WHERE collection_id = $1`, lockerCollectionID); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(t.Context(), 10*time.Second)
	defer cancel()
	blocker, err := db.BeginTx(ctx, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer blocker.Rollback()
	var blockerPID int
	if err := blocker.QueryRowContext(ctx, `SELECT pg_backend_pid()`).Scan(&blockerPID); err != nil {
		t.Fatal(err)
	}
	if _, err := blocker.ExecContext(ctx, `UPDATE collections SET updation_time = 2 WHERE collection_id = $1`, lockerCollectionID); err != nil {
		t.Fatal(err)
	}
	result := make(chan error, 1)
	go func() {
		result <- repository.AddFiles(ctx, lockerCollectionID, userID,
			[]ente.CollectionFileItem{collectionMembershipTestItem(fileID)}, userID)
	}()
	waitForFileAppWriter(t, ctx, db, blockerPID)
	// The initializer cannot see the uncommitted Locker membership yet.
	initialized, err := (&UsageRepository{DB: db}).InitializeFileApp(ctx, userID)
	if err != nil || !initialized {
		t.Fatalf("InitializeFileApp() = (%t, %v), want (true, nil)", initialized, err)
	}
	if err := blocker.Commit(); err != nil {
		t.Fatal(err)
	}
	if err := <-result; err != nil {
		t.Fatal(err)
	}
	assertFileAppReadiness(t, db, userID, false, 1)
}

func TestAddFilesMissingUsageRollsBackMembership(t *testing.T) {
	f := setupFileAppMembershipTest(t, "add")
	if _, err := f.db.Exec(`DELETE FROM usage WHERE user_id = $1`, f.userID); err != nil {
		t.Fatal(err)
	}
	err := f.write(t.Context())
	if err == nil || !strings.Contains(err.Error(), "missing usage row") {
		t.Fatalf("AddFiles() error = %v, want missing usage row", err)
	}
	if !readCollectionMembershipState(t, f.db, f.collectionID, f.fileID).isDeleted {
		t.Fatal("failed write activated the membership")
	}
	assertCollectionMembershipTestUpdationTime(t, f.db, f.collectionID, 1)
}

func TestAddFilesInvalidatesFileOwnerUsageInSharedCollection(t *testing.T) {
	repository, db, fileOwnerID := setupCollectionMembershipTest(t)
	collectionOwnerID := testutil.InsertUser(t, db, testutil.UserFixture{
		UserID: 2, Email: "collection-owner@ente.io", CreationTime: 1,
	})
	collectionID := insertObjectTestCollection(t, db, collectionOwnerID)
	fileID := insertObjectTestFile(t, db, fileOwnerID)
	if err := repository.AddFiles(t.Context(), collectionID, collectionOwnerID,
		[]ente.CollectionFileItem{collectionMembershipTestItem(fileID)}, fileOwnerID); err != nil {
		t.Fatal(err)
	}
	state := readCollectionMembershipState(t, db, collectionID, fileID)
	if state.isDeleted || state.collectionOwnerID != collectionOwnerID || state.fileOwnerID != fileOwnerID {
		t.Fatalf("shared membership = %+v", state)
	}
	assertFileAppReadiness(t, db, fileOwnerID, false, 1)
}

type fileAppMembershipTest struct {
	repo                 *CollectionRepository
	db                   *sql.DB
	userID               int64
	fileID, collectionID int64
	write                func(context.Context) error
}

func setupFileAppMembershipTest(t *testing.T, operation string) fileAppMembershipTest {
	t.Helper()
	repository, db, userID := setupCollectionMembershipTest(t)
	repository.TrashRepo.FileLinkRepo = public.NewFileLinkRepo(db)
	collectionID := insertObjectTestCollection(t, db, userID)
	fileID := insertObjectTestFile(t, db, userID)
	linkObjectTestFileToCollection(t, db, collectionID, fileID, userID)
	if _, err := db.Exec(`UPDATE collection_files SET is_deleted = TRUE
		WHERE collection_id = $1 AND file_id = $2`, collectionID, fileID); err != nil {
		t.Fatal(err)
	}
	items := []ente.CollectionFileItem{collectionMembershipTestItem(fileID)}
	f := fileAppMembershipTest{repo: repository, db: db, userID: userID, fileID: fileID, collectionID: collectionID}
	switch operation {
	case "add":
		f.write = func(ctx context.Context) error {
			return repository.AddFiles(ctx, collectionID, userID, items, userID)
		}
	case "move":
		sourceID := insertObjectTestCollection(t, db, userID)
		linkObjectTestFileToCollection(t, db, sourceID, fileID, userID)
		f.write = func(ctx context.Context) error {
			return repository.MoveFiles(ctx, collectionID, sourceID, items, userID, userID)
		}
	case "restore":
		if _, err := db.Exec(`INSERT INTO trash(file_id, user_id, collection_id, delete_by)
			VALUES ($1, $2, $3, 100)`, fileID, userID, collectionID); err != nil {
			t.Fatal(err)
		}
		f.write = func(ctx context.Context) error {
			return repository.RestoreFiles(ctx, userID, collectionID, items)
		}
	default:
		t.Fatalf("unknown operation %q", operation)
	}
	return f
}

func waitForFileAppWriter(t *testing.T, ctx context.Context, db *sql.DB, blockerPID int) {
	t.Helper()
	for {
		var waiting bool
		if err := db.QueryRowContext(ctx, `SELECT EXISTS (
			SELECT 1 FROM pg_stat_activity WHERE datname = current_database()
			AND $1 = ANY(pg_blocking_pids(pid))
		)`, blockerPID).Scan(&waiting); err != nil {
			t.Fatal(err)
		}
		if waiting {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
}

func assertFileAppReadiness(t *testing.T, db *sql.DB, userID int64, wantReady bool, wantVersion int64) {
	t.Helper()
	var ready bool
	var version int64
	if err := db.QueryRow(`SELECT file_app_ready, file_count_source_version
		FROM usage WHERE user_id = $1`, userID).Scan(&ready, &version); err != nil {
		t.Fatal(err)
	}
	if ready != wantReady || version != wantVersion {
		t.Fatalf("file app state = (%t, %d), want (%t, %d)", ready, version, wantReady, wantVersion)
	}
}
