package model

// LockerItem captures the decrypted metadata for items stored in Ente Locker.
// Locker metadata does not guarantee the same shape as photo metadata, so this
// type keeps the structure flexible while still sharing the common key/nonce
// fields needed for downloads.
type LockerItem struct {
	ID              int64                  `json:"id"`
	OwnerID         int64                  `json:"ownerID"`
	Key             EncString              `json:"key"`
	LastUpdateTime  int64                  `json:"lastUpdateTime"`
	FileNonce       string                 `json:"fileNonce"`
	ThumbnailNonce  string                 `json:"thumbnailNonce"`
	Metadata        map[string]interface{} `json:"metadata"`
	PrivateMetadata map[string]interface{} `json:"privateMetadata"`
	PublicMetadata  map[string]interface{} `json:"publicMetadata"`
	Info            Info                   `json:"info"`
}

// GetFileType returns the Locker item's file type, defaulting to FileTypeOther
// when the metadata does not contain the information.
func (l *LockerItem) GetFileType() FileType {
	if l.Metadata == nil {
		return FileTypeOther
	}
	if value, ok := l.Metadata["fileType"]; ok {
		return coerceFileType(value)
	}
	return FileTypeOther
}
