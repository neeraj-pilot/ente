package repo

import (
	"context"
	"database/sql"

	"github.com/ente/museum/ente"
	"github.com/ente/stacktrace"
	"github.com/lib/pq"
)

// Invalidate after collection changes so usage remains last in the lock order.
func invalidateFileApp(
	ctx context.Context,
	tx *sql.Tx,
	userID int64,
	fileApps []sql.NullString,
	expectedApp ente.App,
) error {
	var exists bool
	err := tx.QueryRowContext(ctx, `WITH invalidated AS (
		UPDATE usage
		SET file_count_source_version = file_count_source_version + 1,
			file_app_ready = FALSE
		WHERE user_id = $1 AND (NOT file_app_ready OR $2)
		RETURNING user_id
	)
	SELECT EXISTS (SELECT 1 FROM invalidated)
		OR EXISTS (SELECT 1 FROM usage WHERE user_id = $1)`,
		userID, !fileAppsMatch(fileApps, expectedApp)).Scan(&exists)
	if err != nil {
		return stacktrace.Propagate(err, "")
	}
	if !exists {
		return stacktrace.NewError("missing usage row for user %d", userID)
	}
	return nil
}

func lockFileApps(ctx context.Context, tx *sql.Tx, userID int64, fileIDs []int64) ([]sql.NullString, error) {
	if len(fileIDs) == 0 {
		return nil, nil
	}
	rows, err := tx.QueryContext(ctx, `SELECT file_id, app
		FROM files
		WHERE owner_id = $1 AND file_id = ANY($2)
		ORDER BY file_id
		FOR UPDATE`, userID, pq.Array(fileIDs))
	if err != nil {
		return nil, stacktrace.Propagate(err, "")
	}
	defer rows.Close()

	fileApps := make([]sql.NullString, 0, len(fileIDs))
	for rows.Next() {
		var fileID int64
		var app sql.NullString
		if err := rows.Scan(&fileID, &app); err != nil {
			return nil, stacktrace.Propagate(err, "")
		}
		fileApps = append(fileApps, app)
	}
	if err := rows.Err(); err != nil {
		return nil, stacktrace.Propagate(err, "")
	}
	return fileApps, nil
}

func fileAppsMatch(fileApps []sql.NullString, expectedApp ente.App) bool {
	if !expectedApp.IsValidForCollection() {
		return false
	}
	for _, fileApp := range fileApps {
		if fileApp.Valid {
			if ente.App(fileApp.String) != expectedApp {
				return false
			}
			continue
		}
		if expectedApp != ente.Photos {
			return false
		}
	}
	return true
}
