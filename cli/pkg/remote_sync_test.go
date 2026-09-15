package pkg

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"github.com/ente/cli/internal/api"
	"github.com/ente/cli/pkg/model"
	"github.com/ente/cli/utils/encoding"
)

func TestFetchRemoteCollectionsAppliesTombstonesWithoutDecrypting(t *testing.T) {
	const (
		knownID   = int64(41)
		unknownID = int64(42)
		updatedAt = int64(900)
	)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if got := r.URL.Query().Get("sinceTime"); got != "100" {
			t.Errorf("sinceTime = %q, want 100", got)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = fmt.Fprintf(w, `{"collections":[{"id":%d,"isDeleted":true,"updationTime":700},{"id":%d,"isDeleted":true,"updationTime":%d}]}`, knownID, unknownID, updatedAt)
	}))
	defer server.Close()

	ctrl, ctx, closeDB := newTestController(t, server.URL)
	defer closeDB()
	want := model.RemoteAlbum{
		ID: knownID, OwnerID: 7, IsShared: true, AlbumName: "Before rename",
		AlbumKey:   model.EncString{CipherText: "key", Nonce: "nonce"},
		PublicMeta: map[string]interface{}{"public": true}, PrivateMeta: map[string]interface{}{"private": true},
		SharedMeta: map[string]interface{}{"shared": true}, LastUpdatedAt: 100,
	}
	if err := ctrl.PutValue(ctx, model.RemoteAlbums, []byte("41"), encoding.MustMarshalJSON(want)); err != nil {
		t.Fatal(err)
	}
	if err := ctrl.PutConfigValue(ctx, model.CollectionsSyncKey, []byte("100")); err != nil {
		t.Fatal(err)
	}

	if err := ctrl.fetchRemoteCollections(ctx); err != nil {
		t.Fatal(err)
	}
	gotJSON, err := ctrl.GetValue(ctx, model.RemoteAlbums, []byte("41"))
	if err != nil {
		t.Fatal(err)
	}
	var got model.RemoteAlbum
	if err := json.Unmarshal(gotJSON, &got); err != nil {
		t.Fatal(err)
	}
	want.IsDeleted = true
	want.LastUpdatedAt = 700
	if string(encoding.MustMarshalJSON(got)) != string(encoding.MustMarshalJSON(want)) {
		t.Fatalf("stored album = %+v, want %+v", got, want)
	}
	if value, err := ctrl.GetValue(ctx, model.RemoteAlbums, []byte("42")); err != nil || value != nil {
		t.Fatalf("unknown tombstone stored as %q, err %v", value, err)
	}
	if cursor, err := ctrl.GetInt64ConfigValue(ctx, model.CollectionsSyncKey); err != nil || cursor != updatedAt {
		t.Fatalf("cursor = %d, err %v; want %d", cursor, err, updatedAt)
	}
}

func newTestController(t *testing.T, host string) (*ClICtrl, context.Context, func()) {
	t.Helper()
	db, err := GetDB(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	account := model.Account{UserID: 1, App: api.AppPhotos}
	if err := createDataBuckets(db, account); err != nil {
		db.Close()
		t.Fatal(err)
	}
	ctx := (&ClICtrl{}).buildRequestContext(context.Background(), account, model.Filter{})
	return &ClICtrl{Client: api.NewClient(api.Params{Host: host}), DB: db}, ctx, func() { _ = db.Close() }
}
