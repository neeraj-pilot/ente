package ente

type CollectionInvitation struct {
	ID                  int64                     `json:"id"`
	CollectionID        int64                     `json:"collectionID"`
	Inviter             BasicUser                 `json:"inviter"`
	Owner               BasicUser                 `json:"owner"`
	EncryptedKey        string                    `json:"encryptedKey"`
	Role                CollectionParticipantRole `json:"role"`
	Name                string                    `json:"name,omitempty"`
	EncryptedName       string                    `json:"encryptedName,omitempty"`
	NameDecryptionNonce string                    `json:"nameDecryptionNonce,omitempty"`
	SharedAt            int64                     `json:"sharedAt"`
}

type AcceptCollectionInvitationsRequest struct {
	InvitationIDs []int64 `json:"invitationIDs" binding:"required,min=1,dive,gt=0"`
}

type RejectCollectionInvitationRequest struct {
	InvitationID int64 `json:"invitationID" binding:"required,gt=0"`
}
