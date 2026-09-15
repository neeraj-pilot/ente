package pkg

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/ente/cli/internal/api"
	"github.com/ente/cli/pkg/model"
	"github.com/ente/cli/pkg/model/export"
	"github.com/ente/cli/utils/encoding"
)

func TestDeletedAlbumCleanupUsesPersistedIDBeforeNameFilter(t *testing.T) {
	tests := []struct {
		name               string
		intermediateRename bool
	}{
		{"without intermediate export", false},
		{"after intermediate export", true},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			ctrl, ctx, closeDB := newTestController(t, "")
			defer closeDB()
			exportDir := t.TempDir()
			account := model.Account{UserID: 1, App: api.AppPhotos, ExportDir: exportDir}
			oldFolder := "Allowed album"
			if err := os.MkdirAll(filepath.Join(exportDir, oldFolder, albumMetaFolder), 0755); err != nil {
				t.Fatal(err)
			}
			meta := export.AlbumMetadata{ID: 41, OwnerID: 1, AlbumName: oldFolder, AccountOwnerIDs: []int64{1}}
			if err := writeJSONToFile(filepath.Join(exportDir, oldFolder, albumMetaFolder, albumMetaFile), meta); err != nil {
				t.Fatal(err)
			}
			album := model.RemoteAlbum{ID: 41, OwnerID: 1, AlbumName: "Renamed album", LastUpdatedAt: 200}
			putAlbum := func() {
				t.Helper()
				if err := ctrl.PutValue(ctx, model.RemoteAlbums, []byte("41"), encoding.MustMarshalJSON(album)); err != nil {
					t.Fatal(err)
				}
			}
			putAlbum()
			if test.intermediateRename {
				if err := ctrl.createLocalFolderForRemoteAlbums(ctx, account); err != nil {
					t.Fatal(err)
				}
			}
			album.IsDeleted = true
			putAlbum()
			filteredCtx := context.WithValue(ctx, model.FilterKey, model.Filter{Albums: []string{oldFolder}})
			if err := ctrl.createLocalFolderForRemoteAlbums(filteredCtx, account); err != nil {
				t.Fatal(err)
			}
			entries, err := os.ReadDir(exportDir)
			if err != nil {
				t.Fatal(err)
			}
			if len(entries) != 0 {
				t.Fatalf("export directory still contains %q", entries[0].Name())
			}
		})
	}
}

func TestSanitizeAlbumFolderName(t *testing.T) {
	tests := []struct {
		name string
		want string
	}{
		{`Trip/2026\Raw`, "Trip_2026_Raw"},
		{`A<B>C:D"E|F?G*H`, "A_B_C_D_E_F_G_H"},
		{"A\x00B\x1fC", "A_B_C"},
		{"Album. ", "Album"},
		{"..", "_"},
		{"COM9.backup", "_COM9.backup"},
		{"LPT³.json", "_LPT³.json"},
		{"NUL .txt", "_NUL .txt"},
		{"COM10", "COM10"},
		{".CON", ".CON"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := sanitizeAlbumFolderName(test.name); got != test.want {
				t.Fatalf("sanitizeAlbumFolderName(%q) = %q, want %q", test.name, got, test.want)
			}
		})
	}
}

func TestUniqueAlbumFolderNameUsesFilesystemCollisions(t *testing.T) {
	root := t.TempDir()
	for _, name := range []string{"Album", "album_1"} {
		if err := os.WriteFile(filepath.Join(root, name), nil, 0600); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := os.Lstat(filepath.Join(root, "album")); os.IsNotExist(err) {
		if err := os.WriteFile(filepath.Join(root, "album"), nil, 0600); err != nil {
			t.Fatal(err)
		}
	}

	got, err := uniqueAlbumFolderName(root, "album")
	if err != nil {
		t.Fatal(err)
	}
	if got != "album_2" {
		t.Fatalf("uniqueAlbumFolderName() = %q, want %q", got, "album_2")
	}
}
