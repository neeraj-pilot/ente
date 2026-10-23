package filedata

import (
	"math"

	"github.com/ente/museum/ente"
)

// The more permissive ratio applies only below 200 MB of preview video data.
const smallVideoPreviewSize = 200_000_000

type VidPreviewRequest struct {
	FileID         int64  `json:"fileID" binding:"required"`
	ObjectID       string `json:"objectID" binding:"required"`
	ObjectSize     int64  `json:"objectSize" binding:"required"`
	Playlist       string `json:"playlist" binding:"required"`
	PlayListHeader string `json:"playlistHeader" binding:"required"`
	Version        *int   `json:"version"`
}

func (r VidPreviewRequest) Validate() error {
	if r.ObjectSize <= 0 {
		return ente.NewBadRequestWithMessage("objectSize must be positive for preview video")
	}
	if r.Playlist == "" || r.PlayListHeader == "" {
		return ente.NewBadRequestWithMessage("playlist and playListHeader are required for preview video")
	}
	return nil
}

// ValidateSize compares the preview object with the stored original file size.
// Previews may be up to 3x the original, or strictly below 4x while under 200 MB.
func (r VidPreviewRequest) ValidateSize(originalSize int64) error {
	if originalSize <= 0 {
		return ente.NewBadRequestWithMessage("original file size is unavailable for preview video")
	}
	if r.ObjectSize <= 0 {
		return ente.NewBadRequestWithMessage("objectSize must be positive for preview video")
	}
	if originalSize > math.MaxInt64/3 || r.ObjectSize <= 3*originalSize {
		return nil
	}
	// Division keeps the strict 4x comparison safe from integer overflow.
	if r.ObjectSize < smallVideoPreviewSize && r.ObjectSize/4 < originalSize {
		return nil
	}
	return ente.NewBadRequestWithMessage("video preview exceeds the allowed size relative to the original file")
}
