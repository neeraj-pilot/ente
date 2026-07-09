package repo

import (
	"context"

	timeutil "github.com/ente/museum/pkg/utils/time"
	"github.com/ente/stacktrace"
)

func (r *SessionsRepository) CreateBrowserSession(ctx context.Context, tokenHash []byte, userID int64, sessionWrapKey string, expiresAt int64) error {
	_, err := r.DB.ExecContext(ctx, `
		INSERT INTO space_browser_sessions (token_hash, user_id, session_wrap_key, expires_at)
		VALUES ($1, $2, $3, $4)
	`, tokenHash, userID, sessionWrapKey, expiresAt)
	return stacktrace.Propagate(err, "")
}

func (r *SessionsRepository) GetBrowserSession(ctx context.Context, tokenHash []byte) (*SpaceBrowserSessionRecord, error) {
	row := r.DB.QueryRowContext(ctx, `
		SELECT s.token_hash, s.user_id, s.session_wrap_key, s.expires_at, s.created_at, s.updated_at, s.last_used_at
		FROM space_browser_sessions s
		WHERE s.token_hash = $1
	`, tokenHash)
	rec := &SpaceBrowserSessionRecord{}
	err := row.Scan(
		&rec.TokenHash,
		&rec.UserID,
		&rec.SessionWrapKey,
		&rec.ExpiresAt,
		&rec.CreatedAt,
		&rec.UpdatedAt,
		&rec.LastUsedAt,
	)
	if err != nil {
		return nil, stacktrace.Propagate(err, "")
	}
	return rec, nil
}

func (r *SessionsRepository) TouchBrowserSession(ctx context.Context, tokenHash []byte) error {
	_, err := r.DB.ExecContext(ctx, `
		UPDATE space_browser_sessions
		SET last_used_at = $1
		WHERE token_hash = $2
	`, timeutil.Microseconds(), tokenHash)
	return stacktrace.Propagate(err, "")
}

func (r *SessionsRepository) DeleteBrowserSession(ctx context.Context, tokenHash []byte) error {
	_, err := r.DB.ExecContext(ctx, `DELETE FROM space_browser_sessions WHERE token_hash = $1`, tokenHash)
	return stacktrace.Propagate(err, "")
}
