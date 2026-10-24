package repo

import (
	"context"
	"database/sql"
	"testing"
	"time"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/internal/testutil"
	"github.com/ente/museum/pkg/utils/s3config"
	"github.com/lib/pq"
	"github.com/spf13/viper"
	"github.com/stretchr/testify/require"
)

func TestFileWritesTakeCollectionAndUsageLocksAfterObjectWork(t *testing.T) {
	for _, tc := range []struct {
		name        string
		write       func(*FileRepository, ente.File) (int64, error)
		wantStorage int64
		wantFiles   int
		wantQueued  int
		wantStaged  int
	}{
		{
			name: "create", wantStorage: 330, wantFiles: 2,
			write: func(repo *FileRepository, file ente.File) (int64, error) {
				file.ID = 0
				created, _, err := repo.Create(file, 200, 20, 220, file.OwnerID, ente.Photos)
				return created.ID, err
			},
		},
		{
			name: "update", wantStorage: 220, wantFiles: 1, wantQueued: 2,
			write: func(repo *FileRepository, file ente.File) (int64, error) {
				err := repo.Update(file, 200, 20, 110,
					[]string{"lock-old-file", "lock-old-thumbnail"},
					[]string{file.File.ObjectKey, file.Thumbnail.ObjectKey})
				return file.ID, err
			},
		},
		{
			name: "thumbnail", wantStorage: 120, wantFiles: 1, wantQueued: 1, wantStaged: 1,
			write: func(repo *FileRepository, file ente.File) (int64, error) {
				oldKey := "lock-old-thumbnail"
				err := repo.UpdateThumbnail(t.Context(), file.ID, file.OwnerID, file.Thumbnail, 20, 10, &oldKey)
				return file.ID, err
			},
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			t.Run("commit", func(t *testing.T) {
				repo, file := setupFileCollectionLockTest(t)
				ctx, cancel := context.WithTimeout(t.Context(), 10*time.Second)
				defer cancel()
				objects, err := repo.DB.BeginTx(ctx, nil)
				require.NoError(t, err)
				defer objects.Rollback()
				var objectPID int
				require.NoError(t, objects.QueryRowContext(ctx, `SELECT pg_backend_pid()`).Scan(&objectPID))
				_, err = objects.ExecContext(ctx, `SELECT 1 FROM temp_objects WHERE object_key = $1 FOR UPDATE`, file.Thumbnail.ObjectKey)
				require.NoError(t, err)
				type result struct {
					id  int64
					err error
				}
				completed := make(chan result, 1)
				go func() {
					id, err := tc.write(repo, file)
					completed <- result{id, err}
				}()
				finished := false
				t.Cleanup(func() {
					if !finished {
						select {
						case <-completed:
						case <-time.After(5 * time.Second):
							t.Error("file write did not finish after releasing its blockers")
						}
					}
				})
				waitForFileWriteBlocker(t, ctx, repo.DB, objectPID)
				// Object work must not hold the collection or usage row. NO KEY
				// UPDATE is compatible with Create's foreign-key KEY SHARE lock.
				probe, err := repo.DB.BeginTx(ctx, nil)
				require.NoError(t, err)
				defer probe.Rollback()
				_, err = probe.ExecContext(ctx, `SELECT 1 FROM collections WHERE collection_id = $1 FOR NO KEY UPDATE NOWAIT`, file.CollectionID)
				require.NoError(t, err)
				require.NoError(t, probe.Rollback())
				usage, err := repo.DB.BeginTx(ctx, nil)
				require.NoError(t, err)
				defer usage.Rollback()
				var usagePID int
				require.NoError(t, usage.QueryRowContext(ctx, `SELECT pg_backend_pid()`).Scan(&usagePID))
				_, err = usage.ExecContext(ctx, `SELECT 1 FROM usage WHERE user_id = $1 FOR UPDATE NOWAIT`, file.OwnerID)
				require.NoError(t, err)
				require.NoError(t, objects.Commit())
				waitForFileWriteBlocker(t, ctx, repo.DB, usagePID)
				// Preserve collection -> usage ordering used by other writers.
				probe, err = repo.DB.BeginTx(ctx, nil)
				require.NoError(t, err)
				defer probe.Rollback()
				_, err = probe.ExecContext(ctx, `SELECT 1 FROM collections WHERE collection_id = $1 FOR NO KEY UPDATE NOWAIT`, file.CollectionID)
				var pgErr *pq.Error
				require.ErrorAs(t, err, &pgErr)
				require.Equal(t, pq.ErrorCode("55P03"), pgErr.Code)
				require.NoError(t, probe.Rollback())
				require.NoError(t, usage.Commit())
				written := <-completed
				finished = true
				require.NoError(t, written.err)
				var collectionTime, membershipTime, fileTime int64
				require.NoError(t, repo.DB.QueryRow(`SELECT c.updation_time, cf.updation_time, f.updation_time
					FROM collections c JOIN collection_files cf USING (collection_id) JOIN files f USING (file_id)
					WHERE c.collection_id = $1 AND f.file_id = $2`, file.CollectionID, written.id).
					Scan(&collectionTime, &membershipTime, &fileTime))
				require.Greater(t, fileTime, int64(1))
				require.Equal(t, fileTime, membershipTime)
				require.Equal(t, fileTime, collectionTime)
				var storage int64
				var photos int
				require.NoError(t, repo.DB.QueryRow(`SELECT storage_consumed, photos_file_count FROM usage WHERE user_id = $1`, file.OwnerID).Scan(&storage, &photos))
				require.Equal(t, tc.wantStorage, storage)
				require.Equal(t, tc.wantFiles, photos)
				assertFileWriteRowCounts(t, repo.DB, tc.wantFiles, tc.wantQueued, tc.wantStaged)
			})
			t.Run("rollback", func(t *testing.T) {
				repo, file := setupFileCollectionLockTest(t)
				_, err := repo.DB.Exec(`DELETE FROM usage WHERE user_id = $1`, file.OwnerID)
				require.NoError(t, err)
				_, err = tc.write(repo, file)
				require.ErrorContains(t, err, "missing usage row")
				var collectionTime, membershipTime, fileTime int64
				require.NoError(t, repo.DB.QueryRow(`SELECT c.updation_time, cf.updation_time, f.updation_time
					FROM collections c JOIN collection_files cf USING (collection_id) JOIN files f USING (file_id)
					WHERE c.collection_id = $1 AND f.file_id = $2`, file.CollectionID, file.ID).
					Scan(&collectionTime, &membershipTime, &fileTime))
				require.Equal(t, int64(1), collectionTime)
				require.Equal(t, int64(1), membershipTime)
				require.Equal(t, int64(1), fileTime)
				assertFileWriteRowCounts(t, repo.DB, 1, 0, 2)
				var newKeys int
				require.NoError(t, repo.DB.QueryRow(`SELECT COUNT(*) FROM object_keys WHERE object_key IN ($1, $2)`, file.File.ObjectKey, file.Thumbnail.ObjectKey).Scan(&newKeys))
				require.Zero(t, newKeys)
			})
		})
	}
}

func TestCreateDuplicateObjectKeepsErrorAndRollsBack(t *testing.T) {
	for _, kind := range []ente.ObjectType{ente.FILE, ente.THUMBNAIL} {
		t.Run(string(kind), func(t *testing.T) {
			repo, file := setupFileCollectionLockTest(t)
			wantErr := ente.ErrDuplicateFileObjectFound
			if kind == ente.FILE {
				file.File.ObjectKey = "lock-old-file"
			} else {
				file.Thumbnail.ObjectKey = "lock-old-thumbnail"
				wantErr = ente.ErrDuplicateThumbnailObjectFound
			}
			_, _, err := repo.Create(file, 200, 20, 220, file.OwnerID, ente.Photos)
			require.ErrorIs(t, err, wantErr)
			assertFileWriteRowCounts(t, repo.DB, 1, 0, 2)
		})
	}
}

func setupFileCollectionLockTest(t *testing.T) (*FileRepository, ente.File) {
	t.Helper()
	viper.Reset()
	viper.Set("s3.hot_storage.primary", "b2-eu-cen")
	t.Cleanup(viper.Reset)
	db := setupFileUsageTest(t)
	ownerID := testutil.InsertUser(t, db, testutil.UserFixture{UserID: 1, Email: "file-lock@example.com", CreationTime: 1})
	_, err := db.Exec(`INSERT INTO usage (user_id, storage_consumed, photos_file_count, locker_file_count) VALUES ($1, 110, 1, 0)`, ownerID)
	require.NoError(t, err)
	collectionID := insertObjectTestCollection(t, db, ownerID)
	fileID := insertObjectTestFile(t, db, ownerID)
	linkObjectTestFileToCollection(t, db, collectionID, fileID, ownerID)
	insertObjectTestKey(t, db, fileID, ente.FILE, "lock-old-file", 100, []string{"b2-eu-cen"})
	insertObjectTestKey(t, db, fileID, ente.THUMBNAIL, "lock-old-thumbnail", 10, []string{"b2-eu-cen"})
	_, err = db.Exec(`INSERT INTO object_copies (object_key, b2, want_b2, want_wasabi, want_scw)
		SELECT object_key, 1, TRUE, TRUE, o_type = 'file' FROM object_keys WHERE file_id = $1`, fileID)
	require.NoError(t, err)
	_, err = db.Exec(`INSERT INTO temp_objects (object_key, expiration_time)
		VALUES ('lock-new-file', 9223372036854775807), ('lock-new-thumbnail', 9223372036854775807)`)
	require.NoError(t, err)
	t.Cleanup(func() {
		_, err := db.Exec(`DELETE FROM queue WHERE queue_name = $1 AND item IN ('lock-old-file', 'lock-old-thumbnail')`, OutdatedObjectsQueue)
		require.NoError(t, err)
	})
	return &FileRepository{
			DB: db, S3Config: s3config.NewS3Config(), QueueRepo: &QueueRepository{DB: db},
			ObjectCleanupRepo: &ObjectCleanupRepository{DB: db}, ObjectCopiesRepo: &ObjectCopiesRepository{DB: db},
		}, ente.File{
			ID: fileID, OwnerID: ownerID, CollectionID: collectionID, UpdationTime: 2,
			File:      ente.FileAttributes{ObjectKey: "lock-new-file", DecryptionHeader: "new-file-header"},
			Thumbnail: ente.FileAttributes{ObjectKey: "lock-new-thumbnail", DecryptionHeader: "new-thumbnail-header"},
			Metadata:  ente.FileAttributes{EncryptedData: "new-metadata", DecryptionHeader: "new-metadata-header"},
			Info:      &ente.FileInfo{FileSize: 200, ThumbnailSize: 20},
		}
}

func waitForFileWriteBlocker(t *testing.T, ctx context.Context, db *sql.DB, pid int) {
	t.Helper()
	for {
		var waiting bool
		require.NoError(t, db.QueryRowContext(ctx, `SELECT EXISTS (
			SELECT 1 FROM pg_stat_activity WHERE datname = current_database()
			AND $1 = ANY(pg_blocking_pids(pid)))`, pid).Scan(&waiting))
		if waiting {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
}

func assertFileWriteRowCounts(t *testing.T, db *sql.DB, files, queued, staged int) {
	t.Helper()
	for _, tc := range []struct {
		query string
		want  int
	}{
		{`SELECT COUNT(*) FROM files`, files},
		{`SELECT COUNT(*) FROM collection_files`, files},
		{`SELECT COUNT(*) FROM object_keys`, 2 * files},
		{`SELECT COUNT(*) FROM object_copies`, 2 * files},
		{`SELECT COUNT(*) FROM temp_objects`, staged},
		{`SELECT COUNT(*) FROM queue WHERE item IN ('lock-old-file', 'lock-old-thumbnail')`, queued},
	} {
		var count int
		require.NoError(t, db.QueryRow(tc.query).Scan(&count))
		require.Equal(t, tc.want, count, tc.query)
	}
}
