package filedata

import (
	"fmt"
	"testing"

	"github.com/ente/museum/ente"
	enteFileData "github.com/ente/museum/ente/filedata"
	"github.com/ente/museum/internal/testutil"
	"github.com/ente/museum/pkg/repo"
	"github.com/stretchr/testify/require"
)

func TestValidateVideoPreviewSizeUsesStoredOriginal(t *testing.T) {
	testutil.WithServerRoot(t)
	db := testutil.RequireTestDB(t)
	testutil.ResetTables(t, db)
	t.Cleanup(func() { testutil.ResetTables(t, db) })
	ownerID := testutil.InsertUser(t, db, testutil.UserFixture{
		UserID: 1, Email: "preview-size@example.com", CreationTime: 1,
	})
	c := &Controller{FileRepo: &repo.FileRepository{DB: db}}
	for _, tc := range []struct {
		name         string
		info         any
		originalSize int64
		previewSize  int64
		onlyThumb    bool
		allowed      bool
	}{
		{"stored size allows small expansion", `{"fileSize":50000000}`, 0, 190_000_000, false, true},
		{"stored size rejects large expansion", `{"fileSize":50000000}`, 0, 200_000_000, false, false},
		{"legacy original below 4x", nil, 40_000_000, 159_999_999, false, true},
		{"legacy original at 4x", nil, 40_000_000, 160_000_000, false, false},
		{"zero metadata falls back", `{"fileSize":0}`, 40_000_000, 159_999_999, false, true},
		{"no original size", nil, 0, 1, false, false},
		{"thumbnail is not original", nil, 500_000_000, 1, true, false},
		{"stored original takes precedence", `{"fileSize":50000000}`, 500_000_000, 200_000_000, false, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var fileID int64
			require.NoError(t, db.QueryRow(`INSERT INTO files
				(owner_id, file_decryption_header, thumbnail_decryption_header,
				 metadata_decryption_header, encrypted_metadata, updation_time, info)
				VALUES ($1, 'file-header', 'thumb-header', 'metadata-header', 'metadata', 1, $2::jsonb)
				RETURNING file_id`, ownerID, tc.info).Scan(&fileID))
			if tc.originalSize > 0 {
				kind := ente.FILE
				if tc.onlyThumb {
					kind = ente.THUMBNAIL
				}
				_, err := db.Exec(`INSERT INTO object_keys (file_id, o_type, object_key, size, datacenters)
					VALUES ($1, $2, $3, $4, ARRAY['b2-eu-cen']::s3region[])`,
					fileID, kind, fmt.Sprintf("preview-original-%d", fileID), tc.originalSize)
				require.NoError(t, err)
			}
			err := c.validateVideoPreviewSize(t.Context(), &enteFileData.VidPreviewRequest{
				FileID: fileID, ObjectSize: tc.previewSize,
			}, ownerID)
			if tc.allowed {
				require.NoError(t, err)
			} else {
				require.Error(t, err)
				var apiError *ente.ApiError
				require.ErrorAs(t, err, &apiError)
				require.Equal(t, ente.BadRequest, apiError.Code)
			}
		})
	}
}
