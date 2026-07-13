package cast

import (
	"database/sql"
	"testing"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/internal/testutil"
	"github.com/google/uuid"
)

func TestInsertCastDataRejectsReplayClaim(t *testing.T) {
	repository, db := newCastRepositoryTest(t)
	deviceID := uuid.New()
	_, err := db.Exec(
		`INSERT INTO casting (id, code, public_key, ip) VALUES ($1, $2, $3, $4)`,
		deviceID,
		"REPLAY",
		"public-key",
		"127.0.0.1",
	)
	if err != nil {
		t.Fatalf("failed to insert casting row: %v", err)
	}
	if err := repository.InsertCastData(t.Context(), 1, "replay", 10, "token-1", "payload-1"); err != nil {
		t.Fatalf("first InsertCastData error = %v", err)
	}
	err = repository.InsertCastData(t.Context(), 2, "replay", 20, "token-2", "payload-2")
	requireCastClaimError(t, err)

	var collectionID, castUser sql.NullInt64
	var token, payload sql.NullString
	err = db.QueryRow(
		`SELECT collection_id, cast_user, token, encrypted_payload FROM casting WHERE id = $1`,
		deviceID,
	).Scan(&collectionID, &castUser, &token, &payload)
	if err != nil {
		t.Fatalf("failed to get casting row: %v", err)
	}
	if !collectionID.Valid || collectionID.Int64 != 10 {
		t.Fatalf("collection_id = %v, want 10", collectionID)
	}
	if !castUser.Valid || castUser.Int64 != 1 {
		t.Fatalf("cast_user = %v, want 1", castUser)
	}
	if !token.Valid || token.String != "token-1" {
		t.Fatalf("token = %v, want token-1", token)
	}
	if !payload.Valid || payload.String != "payload-1" {
		t.Fatalf("encrypted_payload = %v, want payload-1", payload)
	}
}

func TestInsertCastDataRejectsExpiredCode(t *testing.T) {
	repository, db := newCastRepositoryTest(t)
	deviceID := uuid.New()
	_, err := db.Exec(
		`INSERT INTO casting (id, code, public_key, ip, last_used_at)
		 VALUES ($1, $2, $3, $4, now_utc_micro_seconds() - (61::BIGINT * 60 * 1000 * 1000))`,
		deviceID,
		"EXPIRE",
		"public-key",
		"127.0.0.1",
	)
	if err != nil {
		t.Fatalf("failed to insert casting row: %v", err)
	}
	err = repository.InsertCastData(t.Context(), 1, "EXPIRE", 10, "token", "payload")
	requireCastClaimError(t, err)

	var collectionID sql.NullInt64
	err = db.QueryRow(`SELECT collection_id FROM casting WHERE id = $1`, deviceID).Scan(&collectionID)
	if err != nil {
		t.Fatalf("failed to get casting row: %v", err)
	}
	if collectionID.Valid {
		t.Fatalf("expired code was claimed with collection_id = %d", collectionID.Int64)
	}
}

func TestGetEncCastDataRejectsExpiredUnclaimedCode(t *testing.T) {
	repository, db := newCastRepositoryTest(t)
	_, err := db.Exec(
		`INSERT INTO casting (id, code, public_key, ip, last_used_at)
		 VALUES ($1, $2, $3, $4, now_utc_micro_seconds() - (61::BIGINT * 60 * 1000 * 1000))`,
		uuid.New(),
		"STALE1",
		"public-key",
		"127.0.0.1",
	)
	if err != nil {
		t.Fatalf("failed to insert casting row: %v", err)
	}
	payload, err := repository.GetEncCastData(t.Context(), "STALE1")
	if err == nil {
		t.Fatalf("GetEncCastData payload = %v, error = nil, want not found", payload)
	}
	apiErr, ok := err.(*ente.ApiError)
	if !ok {
		t.Fatalf("GetEncCastData error = %T %v, want *ente.ApiError", err, err)
	}
	if apiErr.Code != ente.ErrNotFoundError.Code {
		t.Fatalf("GetEncCastData error code = %s, want %s", apiErr.Code, ente.ErrNotFoundError.Code)
	}
}

func TestGetEncCastDataReturnsExpiredClaimedPayload(t *testing.T) {
	repository, db := newCastRepositoryTest(t)
	_, err := db.Exec(
		`INSERT INTO casting (id, code, public_key, ip, encrypted_payload, last_used_at)
		 VALUES ($1, $2, $3, $4, $5, now_utc_micro_seconds() - (61::BIGINT * 60 * 1000 * 1000))`,
		uuid.New(),
		"CLAIM1",
		"public-key",
		"127.0.0.1",
		"payload",
	)
	if err != nil {
		t.Fatalf("failed to insert casting row: %v", err)
	}
	payload, err := repository.GetEncCastData(t.Context(), "CLAIM1")
	if err != nil {
		t.Fatalf("GetEncCastData error = %v", err)
	}
	if payload == nil || *payload != "payload" {
		t.Fatalf("GetEncCastData payload = %v, want payload", payload)
	}
}

func TestRevokeForGivenDeviceIDOnlyDeletesUserDevice(t *testing.T) {
	repository, db := newCastRepositoryTest(t)
	deviceID := uuid.New()
	ownerID := int64(1)
	otherUserID := int64(2)
	_, err := db.Exec(
		`INSERT INTO casting (id, code, public_key, cast_user, ip) VALUES ($1, $2, $3, $4, $5)`,
		deviceID,
		"ABC123",
		"public-key",
		ownerID,
		"127.0.0.1",
	)
	if err != nil {
		t.Fatalf("failed to insert casting row: %v", err)
	}
	if err := repository.RevokeForGivenUserAndDevice(t.Context(), otherUserID, deviceID); err != nil {
		t.Fatalf("RevokeForGivenDeviceID other user error = %v", err)
	}
	if isDeleted := getCastDeviceIsDeleted(t, db, deviceID); isDeleted {
		t.Fatal("other user should not delete device")
	}
	if err := repository.RevokeForGivenUserAndDevice(t.Context(), ownerID, deviceID); err != nil {
		t.Fatalf("RevokeForGivenDeviceID owner error = %v", err)
	}
	if isDeleted := getCastDeviceIsDeleted(t, db, deviceID); !isDeleted {
		t.Fatal("owner should delete device")
	}
}

func newCastRepositoryTest(t *testing.T) (*Repository, *sql.DB) {
	t.Helper()
	testutil.WithServerRoot(t)
	db := testutil.RequireTestDB(t)
	testutil.ResetTables(t, db)
	t.Cleanup(func() {
		testutil.ResetTables(t, db)
	})
	return &Repository{DB: db}, db
}

func requireCastClaimError(t *testing.T, err error) {
	t.Helper()
	if err == nil {
		t.Fatal("InsertCastData error = nil, want cast claim error")
	}
	apiErr, ok := err.(*ente.ApiError)
	if !ok {
		t.Fatalf("InsertCastData error = %T %v, want *ente.ApiError", err, err)
	}
	if apiErr.Code != ente.ErrSessionAlreadyClaimed.Code {
		t.Fatalf("InsertCastData error code = %s, want %s", apiErr.Code, ente.ErrSessionAlreadyClaimed.Code)
	}
}

func getCastDeviceIsDeleted(t *testing.T, db *sql.DB, deviceID uuid.UUID) bool {
	t.Helper()
	var isDeleted bool
	err := db.QueryRow(`SELECT is_deleted FROM casting WHERE id = $1`, deviceID).Scan(&isDeleted)
	if err != nil {
		t.Fatalf("failed to get casting row: %v", err)
	}
	return isDeleted
}
