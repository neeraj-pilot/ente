package repo

import (
	"context"
	"testing"
	"time"

	"github.com/ente/museum/ente"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/stretchr/testify/require"
)

func TestUploadRemainsVisibleAfterNewerCommit(t *testing.T) {
	for _, concurrentWrite := range []string{"upload", "rename"} {
		t.Run(concurrentWrite, func(t *testing.T) {
			repository, file := setupFileCollectionLockTest(t)
			collections := &CollectionRepository{DB: repository.DB, LatencyLogger: prometheus.NewHistogramVec(
				prometheus.HistogramOpts{Name: "collection_sync_test"}, []string{"method"})}
			ctx, cancel := context.WithTimeout(t.Context(), 10*time.Second)
			defer cancel()
			blocker, err := repository.DB.BeginTx(ctx, nil)
			require.NoError(t, err)
			defer blocker.Rollback()
			var pid int
			require.NoError(t, blocker.QueryRowContext(ctx, `SELECT pg_backend_pid()`).Scan(&pid))
			_, err = blocker.ExecContext(ctx, `SELECT 1 FROM temp_objects WHERE object_key = $1 FOR UPDATE`, file.Thumbnail.ObjectKey)
			require.NoError(t, err)
			type result struct {
				file ente.File
				err  error
			}
			completed := make(chan result, 1)
			go func() {
				created, _, err := repository.Create(file, 200, 20, 220, file.OwnerID, ente.Photos)
				completed <- result{created, err}
			}()
			finished := false
			t.Cleanup(func() {
				blocker.Rollback()
				if !finished {
					select {
					case <-completed:
					case <-time.After(5 * time.Second):
						t.Error("upload did not finish after releasing its blocker")
					}
				}
			})
			waitForFileWriteBlocker(t, ctx, repository.DB, pid)
			if concurrentWrite == "rename" {
				require.NoError(t, collections.Rename(file.CollectionID, "renamed", "nonce"))
			} else {
				newer := file
				newer.UpdationTime = time.Now().UnixMicro()
				newer.File.ObjectKey = "concurrent-file"
				newer.Thumbnail.ObjectKey = "concurrent-thumbnail"
				_, err := repository.DB.ExecContext(ctx, `INSERT INTO temp_objects (object_key, expiration_time)
					VALUES ($1, 9223372036854775807), ($2, 9223372036854775807)`, newer.File.ObjectKey, newer.Thumbnail.ObjectKey)
				require.NoError(t, err)
				_, _, err = repository.Create(newer, 200, 20, 220, file.OwnerID, ente.Photos)
				require.NoError(t, err)
			}
			var cursor int64
			require.NoError(t, repository.DB.QueryRowContext(ctx, `SELECT updation_time FROM collections WHERE collection_id = $1`, file.CollectionID).Scan(&cursor))
			// Clients advance to this album version even when its file diff is empty.
			_, err = collections.GetDiff(file.CollectionID, 1, 100)
			require.NoError(t, err)
			require.NoError(t, blocker.Commit())
			written := <-completed
			finished = true
			require.NoError(t, written.err)
			require.Greater(t, written.file.UpdationTime, cursor)
			diff, err := collections.GetDiff(file.CollectionID, cursor, 100)
			require.NoError(t, err)
			require.Len(t, diff, 1)
			require.Equal(t, written.file.ID, diff[0].ID)
			require.Equal(t, written.file.UpdationTime, diff[0].UpdationTime)
		})
	}
}

func TestFileCreationAdvancesPastCollectionVersion(t *testing.T) {
	for _, operation := range []string{"create", "metadata file"} {
		t.Run(operation, func(t *testing.T) {
			repository, file := setupFileCollectionLockTest(t)
			previous := time.Now().Add(time.Hour).UnixMicro()
			_, err := repository.DB.Exec(`UPDATE collections SET updation_time = $1 WHERE collection_id = $2`, previous, file.CollectionID)
			require.NoError(t, err)
			var returnedVersion int64
			switch operation {
			case "create":
				file, _, err = repository.Create(file, 200, 20, 220, file.OwnerID, ente.Photos)
				returnedVersion = file.UpdationTime
			case "metadata file":
				created, createErr := repository.CreateMetaFile(ente.MetaFile{
					OwnerID: file.OwnerID, CollectionID: file.CollectionID,
					Metadata: file.Metadata, UpdationTime: file.UpdationTime,
				}, file.OwnerID, ente.Photos)
				require.NoError(t, createErr)
				file.ID, returnedVersion = created.ID, created.UpdationTime
			}
			require.NoError(t, err)
			var collectionTime, membershipTime, fileTime int64
			require.NoError(t, repository.DB.QueryRow(`SELECT c.updation_time, cf.updation_time, f.updation_time
				FROM collections c JOIN collection_files cf USING (collection_id) JOIN files f USING (file_id)
				WHERE c.collection_id = $1 AND f.file_id = $2`, file.CollectionID, file.ID).
				Scan(&collectionTime, &membershipTime, &fileTime))
			require.Equal(t, previous+1, collectionTime)
			require.Equal(t, collectionTime, membershipTime)
			require.Equal(t, collectionTime, fileTime)
			require.Equal(t, fileTime, returnedVersion)
		})
	}
}
