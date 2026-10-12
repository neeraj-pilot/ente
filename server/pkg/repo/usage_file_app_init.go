package repo

import (
	"context"
	"errors"
	"fmt"
)

var ErrFileAppIneligible = errors.New("file app provenance is ineligible")

type fileAppInitSnapshot struct {
	version             int64
	ready               bool
	ineligibilityReason string
}

// Call only after every server instance uses the membership invalidation
// protocol; older writers can leave a successful snapshot stale.
func (repo *UsageRepository) InitializeFileApp(ctx context.Context, userID int64) (bool, error) {
	snapshot, err := repo.readFileAppInitSnapshot(ctx, userID)
	if err != nil || snapshot.ready {
		return false, err
	}
	if snapshot.ineligibilityReason != "" {
		return false, fmt.Errorf("%w: %s", ErrFileAppIneligible, snapshot.ineligibilityReason)
	}
	return repo.initializeFileAppAtVersion(ctx, userID, snapshot.version)
}

func (repo *UsageRepository) readFileAppInitSnapshot(ctx context.Context, userID int64) (fileAppInitSnapshot, error) {
	var snapshot fileAppInitSnapshot
	err := repo.DB.QueryRowContext(ctx, `WITH locker_candidates AS MATERIALIZED (
		SELECT DISTINCT f.file_id, f.app
		FROM collections AS locker_collection
		JOIN collection_files AS cf ON cf.collection_id = locker_collection.collection_id
		JOIN files AS f ON f.file_id = cf.file_id AND f.owner_id = $1
		WHERE locker_collection.owner_id = $1 AND locker_collection.app = 'locker'
	), source AS (
		SELECT CASE
			WHEN EXISTS (
				SELECT 1
				FROM files AS f
				JOIN collection_files AS cf ON cf.file_id = f.file_id
				JOIN collections AS c ON c.collection_id = cf.collection_id
				WHERE f.owner_id = $1 AND f.app IS NOT NULL AND f.app IS DISTINCT FROM c.app
			) THEN 'Explicit file app does not match collection history'
			WHEN EXISTS (
				SELECT 1
				FROM locker_candidates AS candidate
				JOIN collection_files AS cf ON cf.file_id = candidate.file_id
				JOIN collections AS c ON c.collection_id = cf.collection_id
				WHERE c.app IS DISTINCT FROM 'locker'
			) THEN 'Locker candidate has cross-app or unsupported-app history'
			ELSE ''
		END AS ineligibility_reason
	)
	SELECT u.file_count_source_version, u.file_app_ready, source.ineligibility_reason
	FROM usage AS u CROSS JOIN source
	WHERE u.user_id = $1`, userID).Scan(&snapshot.version, &snapshot.ready, &snapshot.ineligibilityReason)
	return snapshot, err
}

func (repo *UsageRepository) initializeFileAppAtVersion(ctx context.Context, userID int64, version int64) (bool, error) {
	tx, err := repo.DB.BeginTx(ctx, nil)
	if err != nil {
		return false, err
	}
	defer tx.Rollback()

	// Match membership writers' file lock order, and hold these locks until the
	// version check succeeds so a stale backfill cannot leave relabeled files.
	_, err = tx.ExecContext(ctx, `WITH candidates AS MATERIALIZED (
		SELECT f.file_id
		FROM files AS f
		WHERE f.owner_id = $1 AND f.app IS NULL AND EXISTS (
			SELECT 1 FROM collection_files AS cf
			JOIN collections AS c ON c.collection_id = cf.collection_id
			WHERE cf.file_id = f.file_id AND c.owner_id = $1 AND c.app = 'locker'
		)
		ORDER BY f.file_id
		FOR UPDATE OF f
	)
	UPDATE files AS f SET app = 'locker'
	FROM candidates WHERE f.file_id = candidates.file_id`, userID)
	if err != nil {
		return false, err
	}
	result, err := tx.ExecContext(ctx, `UPDATE usage
		SET file_app_ready = TRUE
		WHERE user_id = $1 AND file_count_source_version = $2 AND file_app_ready = FALSE`, userID, version)
	if err != nil {
		return false, err
	}
	updated, err := result.RowsAffected()
	if err != nil || updated == 0 {
		return false, err
	}
	if err := tx.Commit(); err != nil {
		return false, err
	}
	return true, nil
}
