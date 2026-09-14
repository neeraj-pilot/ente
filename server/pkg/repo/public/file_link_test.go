package public

import (
	"database/sql"
	"errors"
	"testing"
	"time"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/internal/testutil"
	"github.com/patrickmn/go-cache"
)

func TestGetFileUrlRowByTokenReturnsActiveRowBeforeDisabledRow(t *testing.T) {
	repository, db := setupFileLinkRepositoryTest(t)

	insertFileLinkToken(t, db, "pft_disabled", "reused-token", 1, 11, true)
	insertFileLinkToken(t, db, "pft_active", "reused-token", 2, 22, false)

	row, err := repository.GetFileUrlRowByToken(t.Context(), "reused-token")
	if err != nil {
		t.Fatalf("GetFileUrlRowByToken() error = %v", err)
	}
	if row.LinkID != "pft_active" || row.FileID != 2 || row.OwnerID != 22 || row.IsDisabled {
		t.Fatalf("expected active row, got %+v", row)
	}
}

func TestGetFileUrlRowByTokenReturnsDisabledRowWhenNoActiveRowExists(t *testing.T) {
	repository, db := setupFileLinkRepositoryTest(t)

	insertFileLinkToken(t, db, "pft_disabled", "disabled-token", 1, 11, true)

	row, err := repository.GetFileUrlRowByToken(t.Context(), "disabled-token")
	if err != nil {
		t.Fatalf("GetFileUrlRowByToken() error = %v", err)
	}
	if row.LinkID != "pft_disabled" || !row.IsDisabled {
		t.Fatalf("expected disabled row, got %+v", row)
	}
}

func TestGetFileUrlRowByTokenReturnsNotFoundForUnknownToken(t *testing.T) {
	repository, _ := setupFileLinkRepositoryTest(t)

	_, err := repository.GetFileUrlRowByToken(t.Context(), "missing-token")
	if !errors.Is(err, ente.ErrNotFound) {
		t.Fatalf("expected ErrNotFound, got %v", err)
	}
}

func TestDisableLinksForUserInvalidatesTheirCacheEntries(t *testing.T) {
	repository, db := setupFileLinkRepositoryTest(t)
	repository.Cache = cache.New(time.Minute, time.Minute)

	insertFileLinkToken(t, db, "pft_owner_1", "owner-token-1", 1, 11, false)
	insertFileLinkToken(t, db, "pft_owner_2", "owner-token-2", 2, 11, false)
	insertFileLinkToken(t, db, "pft_other", "other-token", 3, 22, false)
	versions := map[string]string{}
	for _, token := range []string{"owner-token-1", "owner-token-2", "other-token"} {
		versions[token] = LinkCacheVersion(repository.Cache, token)
	}

	if err := repository.DisableLinksForUser(t.Context(), 11); err != nil {
		t.Fatalf("DisableLinksForUser() error = %v", err)
	}
	for _, token := range []string{"owner-token-1", "owner-token-2"} {
		if LinkCacheVersion(repository.Cache, token) == versions[token] {
			t.Fatalf("cache entry %q was not invalidated", token)
		}
	}
	if LinkCacheVersion(repository.Cache, "other-token") != versions["other-token"] {
		t.Fatal("unaffected cache entry was invalidated")
	}
}

func setupFileLinkRepositoryTest(t *testing.T) (*FileLinkRepository, *sql.DB) {
	t.Helper()

	testutil.WithServerRoot(t)
	db := testutil.RequireTestDB(t)
	testutil.ResetTables(t, db)
	resetFileLinkTables(t, db)
	t.Cleanup(func() {
		testutil.ResetTables(t, db)
		resetFileLinkTables(t, db)
	})

	return NewFileLinkRepo(db), db
}

func resetFileLinkTables(t *testing.T, db *sql.DB) {
	t.Helper()

	_, err := db.Exec(`TRUNCATE TABLE public_file_tokens_access_history, public_file_tokens RESTART IDENTITY CASCADE`)
	if err != nil {
		t.Fatalf("failed to reset public file token tables: %v", err)
	}
}

func insertFileLinkToken(t *testing.T, db *sql.DB, id string, accessToken string, fileID int64, ownerID int64, isDisabled bool) {
	t.Helper()

	_, err := db.Exec(
		`INSERT INTO public_file_tokens(id, file_id, owner_id, app, access_token, is_disabled)
		 VALUES($1, $2, $3, $4, $5, $6)`,
		id,
		fileID,
		ownerID,
		"photos",
		accessToken,
		isDisabled,
	)
	if err != nil {
		t.Fatalf("failed to insert public file token %q: %v", id, err)
	}
}
