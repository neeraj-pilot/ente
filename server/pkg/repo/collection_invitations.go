package repo

import (
	"context"
	"database/sql"
	"math"
	"sort"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/pkg/utils/crypto"
	emailUtil "github.com/ente/museum/pkg/utils/email"
	"github.com/ente/museum/pkg/utils/time"
	"github.com/lib/pq"
)

type collectionInvitationRow struct {
	ID           int64
	CollectionID int64
	OwnerID      int64
	InviterID    int64
	EncryptedKey string
	Role         ente.CollectionParticipantRole
	SharedAt     int64
}

func lockShareRecipients(ctx context.Context, tx *sql.Tx, userIDs []int64, requireActive bool) error {
	rows, err := tx.QueryContext(ctx, `SELECT user_id, encrypted_email IS NOT NULL FROM users
		WHERE user_id = ANY($1) ORDER BY user_id FOR NO KEY UPDATE`, pq.Array(userIDs))
	if err != nil {
		return err
	}
	defer rows.Close()
	active := make(map[int64]bool, len(userIDs))
	for rows.Next() {
		var id int64
		var valid bool
		if err := rows.Scan(&id, &valid); err != nil {
			return err
		}
		active[id] = valid
	}
	if err := rows.Err(); err != nil {
		return err
	}
	for _, id := range userIDs {
		if _, found := active[id]; !found || (requireActive && !active[id]) {
			return ente.ErrPermissionDenied
		}
	}
	return nil
}

func (repo *CollectionRepository) HasPendingInvitations(ctx context.Context, userID int64) (bool, error) {
	var pending bool
	err := repo.DB.QueryRowContext(ctx, `SELECT EXISTS (
		SELECT 1 FROM collection_invitations ci
		JOIN collections c ON c.collection_id = ci.collection_id
		JOIN users inviter ON inviter.user_id = ci.invited_by_user_id
		JOIN users owner ON owner.user_id = c.owner_id
		WHERE ci.to_user_id = $1 AND c.is_deleted = FALSE AND c.app = 'photos'
			AND inviter.encrypted_email IS NOT NULL AND owner.encrypted_email IS NOT NULL
	)`, userID).Scan(&pending)
	return pending, err
}

func (repo *CollectionRepository) ListPendingInvitations(ctx context.Context, userID, beforeID int64, limit int) ([]ente.CollectionInvitation, bool, error) {
	if beforeID == 0 {
		beforeID = math.MaxInt64
	}
	rows, err := repo.DB.QueryContext(ctx, `SELECT ci.invitation_id, ci.collection_id, ci.invited_by_user_id,
		inviter.encrypted_email, inviter.email_decryption_nonce, ci.encrypted_key, ci.role_type,
		c.owner_id, owner.encrypted_email, owner.email_decryption_nonce,
		c.name, c.encrypted_name, c.name_decryption_nonce, ci.shared_at
		FROM collection_invitations ci
		JOIN collections c ON c.collection_id = ci.collection_id
		JOIN users inviter ON inviter.user_id = ci.invited_by_user_id
		JOIN users owner ON owner.user_id = c.owner_id
		WHERE ci.to_user_id = $1 AND c.is_deleted = FALSE AND c.app = 'photos'
			AND inviter.encrypted_email IS NOT NULL AND owner.encrypted_email IS NOT NULL
			AND ci.invitation_id < $2
		ORDER BY ci.invitation_id DESC LIMIT $3`, userID, beforeID, limit+1)
	if err != nil {
		return nil, false, err
	}
	defer rows.Close()
	invitations := make([]ente.CollectionInvitation, 0)
	for rows.Next() {
		var invitation ente.CollectionInvitation
		var name, encryptedName, nameNonce sql.NullString
		var email, nonce, ownerEmail, ownerNonce []byte
		if err := rows.Scan(&invitation.ID, &invitation.CollectionID, &invitation.Inviter.ID,
			&email, &nonce, &invitation.EncryptedKey, &invitation.Role,
			&invitation.Owner.ID, &ownerEmail, &ownerNonce, &name, &encryptedName, &nameNonce,
			&invitation.SharedAt); err != nil {
			return nil, false, err
		}
		invitation.Inviter.Email, err = crypto.Decrypt(email, repo.SecretEncryptionKey, nonce)
		if err != nil {
			return nil, false, err
		}
		invitation.Owner.Email, err = crypto.Decrypt(ownerEmail, repo.SecretEncryptionKey, ownerNonce)
		if err != nil {
			return nil, false, err
		}
		invitation.Name = name.String
		invitation.EncryptedName = encryptedName.String
		invitation.NameDecryptionNonce = nameNonce.String
		invitations = append(invitations, invitation)
	}
	if err := rows.Err(); err != nil {
		return nil, false, err
	}
	hasMore := len(invitations) > limit
	if hasMore {
		invitations = invitations[:limit]
	}
	return invitations, hasMore, nil
}

func (repo *CollectionRepository) ShareWithAcceptance(ctx context.Context, collectionID, ownerID, inviterID int64,
	shares []CollectionShareItem, updationTime int64) error {
	if len(shares) == 0 || inviterID <= 0 {
		return ente.ErrBadRequest
	}
	tx, err := repo.DB.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	recipientIDs := make([]int64, 0, len(shares))
	for _, share := range shares {
		recipientIDs = append(recipientIDs, share.ToUserID)
	}
	lockedUserIDs := append(append([]int64(nil), recipientIDs...), inviterID, ownerID)
	if err := lockShareRecipients(ctx, tx, lockedUserIDs, true); err != nil {
		return err
	}
	acceptanceEnabled, err := repo.shareAcceptanceEnabled(ctx, tx, inviterID)
	if err != nil {
		return err
	}
	var actualOwnerID int64
	var app string
	var deleted bool
	if err := tx.QueryRowContext(ctx, `SELECT owner_id, app, is_deleted FROM collections WHERE collection_id = $1`,
		collectionID).Scan(&actualOwnerID, &app, &deleted); err != nil {
		return err
	}
	if deleted || app != string(ente.Photos) || actualOwnerID != ownerID {
		return ente.ErrCollectionDeleted
	}
	if inviterID != ownerID {
		var admin bool
		if err := tx.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM collection_shares
			WHERE collection_id = $1 AND to_user_id = $2 AND is_deleted = FALSE AND role_type = 'ADMIN')`,
			collectionID, inviterID).Scan(&admin); err != nil {
			return err
		}
		if !admin {
			return ente.ErrPermissionDenied
		}
	}
	for _, share := range shares {
		if !validInvitationRole(share.Role) || share.ToUserID == ownerID || share.ToUserID == inviterID {
			return ente.ErrBadRequest
		}
		var alreadyShared bool
		if err := tx.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM collection_shares
			WHERE collection_id = $1 AND to_user_id = $2 AND is_deleted = FALSE)`,
			collectionID, share.ToUserID).Scan(&alreadyShared); err != nil {
			return err
		}
		trusted := false
		if acceptanceEnabled {
			trusted, err = trustedCollectionInviter(ctx, tx, share.ToUserID, inviterID)
			if err != nil {
				return err
			}
		}
		if !acceptanceEnabled || alreadyShared || trusted {
			if _, err := tx.ExecContext(ctx, `DELETE FROM collection_invitations
				WHERE collection_id = $1 AND to_user_id = $2`, collectionID, share.ToUserID); err != nil {
				return err
			}
			if _, err := tx.ExecContext(ctx, `INSERT INTO collection_shares
				(collection_id, from_user_id, to_user_id, encrypted_key, updation_time, role_type, shared_at)
				VALUES ($1, $2, $3, $4, $5, $6, $5)
				ON CONFLICT (collection_id, from_user_id, to_user_id) DO UPDATE SET
					is_deleted = FALSE, updation_time = EXCLUDED.updation_time, role_type = EXCLUDED.role_type,
					shared_at = CASE WHEN collection_shares.is_deleted THEN EXCLUDED.shared_at ELSE collection_shares.shared_at END`,
				collectionID, ownerID, share.ToUserID, share.EncryptedKey, updationTime, share.Role); err != nil {
				return err
			}
			continue
		}
		if _, err := tx.ExecContext(ctx, `DELETE FROM collection_invitations
			WHERE collection_id = $1 AND to_user_id = $2
				AND (invited_by_user_id <> $3 OR encrypted_key <> $4 OR role_type <> $5)`,
			collectionID, share.ToUserID, inviterID, share.EncryptedKey, share.Role); err != nil {
			return err
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO collection_invitations
			(collection_id, to_user_id, invited_by_user_id, encrypted_key, role_type, shared_at)
			VALUES ($1, $2, $3, $4, $5, $6)
			ON CONFLICT (collection_id, to_user_id) DO NOTHING`,
			collectionID, share.ToUserID, inviterID, share.EncryptedKey, share.Role, updationTime); err != nil {
			return err
		}
	}
	if err := withdrawInvitationsOfNonAdmins(ctx, tx, collectionID, recipientIDs); err != nil {
		return err
	}
	result, err := tx.ExecContext(ctx, `UPDATE collections SET updation_time = GREATEST(updation_time + 1, $1)
		WHERE collection_id = $2 AND is_deleted = FALSE`, updationTime, collectionID)
	if err != nil {
		return err
	}
	updated, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if updated != 1 {
		return ente.ErrCollectionDeleted
	}
	return tx.Commit()
}

func (repo *CollectionRepository) shareAcceptanceEnabled(ctx context.Context, tx *sql.Tx, inviterID int64) (bool, error) {
	var encryptedEmail, nonce []byte
	var internalUser bool
	err := tx.QueryRowContext(ctx, `SELECT u.encrypted_email, u.email_decryption_nonce,
		EXISTS(SELECT 1 FROM remote_store r
			WHERE r.user_id = u.user_id AND r.key_name = $2 AND r.key_value = 'true')
		FROM users u WHERE u.user_id = $1`, inviterID, string(ente.IsInternalUser)).
		Scan(&encryptedEmail, &nonce, &internalUser)
	if err != nil {
		return false, err
	}
	if internalUser {
		return true, nil
	}
	email, err := crypto.Decrypt(encryptedEmail, repo.SecretEncryptionKey, nonce)
	if err != nil {
		return false, err
	}
	return emailUtil.IsEnteDomain(email), nil
}

func withdrawInvitationsOfNonAdmins(ctx context.Context, tx *sql.Tx, collectionID int64, userIDs []int64) error {
	_, err := tx.ExecContext(ctx, `DELETE FROM collection_invitations ci
		WHERE ci.collection_id = $1 AND ci.invited_by_user_id = ANY($2)
			AND NOT EXISTS (SELECT 1 FROM collection_shares cs
				WHERE cs.collection_id = ci.collection_id AND cs.to_user_id = ci.invited_by_user_id
					AND cs.is_deleted = FALSE AND cs.role_type = 'ADMIN')`,
		collectionID, pq.Array(userIDs))
	return err
}

func trustedCollectionInviter(ctx context.Context, tx *sql.Tx, recipientID, inviterID int64) (bool, error) {
	var sameFamily, storedTrust, existingShare bool
	err := tx.QueryRowContext(ctx, `SELECT
		EXISTS (SELECT 1 FROM users recipient JOIN users inviter
			ON recipient.family_admin_id = inviter.family_admin_id
			WHERE recipient.user_id = $1 AND inviter.user_id = $2 AND recipient.family_admin_id IS NOT NULL),
		EXISTS (SELECT 1 FROM trusted_sharers
			WHERE to_user_id = $1 AND sharer_user_id = $2),
		EXISTS (SELECT 1 FROM collection_shares cs JOIN collections c ON c.collection_id = cs.collection_id
			WHERE cs.to_user_id = $1 AND cs.from_user_id = $2
				AND cs.is_deleted = FALSE AND c.is_deleted = FALSE)`,
		recipientID, inviterID).Scan(&sameFamily, &storedTrust, &existingShare)
	if err != nil {
		return false, err
	}
	if existingShare && !storedTrust {
		if _, err := tx.ExecContext(ctx, `INSERT INTO trusted_sharers (to_user_id, sharer_user_id)
			VALUES ($1, $2) ON CONFLICT DO NOTHING`, recipientID, inviterID); err != nil {
			return false, err
		}
	}
	return sameFamily || storedTrust || existingShare, nil
}

func (repo *CollectionRepository) AcceptInvitations(ctx context.Context, userID int64, invitationIDs []int64) ([]int64, error) {
	ids, err := sortedInvitationIDs(invitationIDs)
	if err != nil {
		return nil, err
	}
	tx, err := repo.DB.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()
	userRows, err := tx.QueryContext(ctx, `SELECT c.owner_id, ci.invited_by_user_id
		FROM collection_invitations ci
		JOIN collections c ON c.collection_id = ci.collection_id
		WHERE ci.invitation_id = ANY($1) AND ci.to_user_id = $2`, pq.Array(ids), userID)
	if err != nil {
		return nil, err
	}
	userIDs := []int64{userID}
	for userRows.Next() {
		var ownerID, inviterID int64
		if err := userRows.Scan(&ownerID, &inviterID); err != nil {
			userRows.Close()
			return nil, err
		}
		userIDs = append(userIDs, ownerID, inviterID)
	}
	err = userRows.Err()
	userRows.Close()
	if err != nil {
		return nil, err
	}
	if err := lockShareRecipients(ctx, tx, userIDs, true); err != nil {
		return nil, err
	}
	rows, err := tx.QueryContext(ctx, `SELECT ci.invitation_id, ci.collection_id, c.owner_id,
		ci.invited_by_user_id, ci.encrypted_key, ci.role_type, ci.shared_at,
		c.app, c.is_deleted, owner.encrypted_email IS NOT NULL,
		inviter.encrypted_email IS NOT NULL
		FROM collection_invitations ci
		JOIN collections c ON c.collection_id = ci.collection_id
		JOIN users owner ON owner.user_id = c.owner_id
		JOIN users inviter ON inviter.user_id = ci.invited_by_user_id
		WHERE ci.invitation_id = ANY($1) AND ci.to_user_id = $2
		ORDER BY ci.invitation_id FOR UPDATE OF ci`, pq.Array(ids), userID)
	if err != nil {
		return nil, err
	}
	invitations := make([]collectionInvitationRow, 0, len(ids))
	for rows.Next() {
		var invitation collectionInvitationRow
		var app string
		var deleted, ownerActive, inviterActive bool
		if err := rows.Scan(&invitation.ID, &invitation.CollectionID, &invitation.OwnerID,
			&invitation.InviterID, &invitation.EncryptedKey, &invitation.Role, &invitation.SharedAt,
			&app, &deleted, &ownerActive, &inviterActive); err != nil {
			rows.Close()
			return nil, err
		}
		if app != string(ente.Photos) || deleted || !ownerActive || !inviterActive || !validInvitationRole(invitation.Role) {
			rows.Close()
			return nil, ente.ErrPermissionDenied
		}
		invitations = append(invitations, invitation)
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return nil, err
	}
	if len(invitations) != len(ids) {
		return nil, ente.ErrPermissionDenied
	}
	collectionIDs := make([]int64, 0, len(ids))
	for _, invitation := range invitations {
		collectionIDs = append(collectionIDs, invitation.CollectionID)
	}
	sort.Slice(collectionIDs, func(i, j int) bool { return collectionIDs[i] < collectionIDs[j] })
	collectionRows, err := tx.QueryContext(ctx, `SELECT collection_id, is_deleted FROM collections
		WHERE collection_id = ANY($1) ORDER BY collection_id FOR NO KEY UPDATE`, pq.Array(collectionIDs))
	if err != nil {
		return nil, err
	}
	lockedCollections := 0
	for collectionRows.Next() {
		var collectionID int64
		var deleted bool
		if err := collectionRows.Scan(&collectionID, &deleted); err != nil {
			collectionRows.Close()
			return nil, err
		}
		if deleted {
			collectionRows.Close()
			return nil, ente.ErrPermissionDenied
		}
		lockedCollections++
	}
	err = collectionRows.Err()
	collectionRows.Close()
	if err != nil {
		return nil, err
	}
	if lockedCollections != len(collectionIDs) {
		return nil, ente.ErrPermissionDenied
	}
	for _, invitation := range invitations {
		if invitation.InviterID != invitation.OwnerID {
			var admin bool
			if err := tx.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM collection_shares
				WHERE collection_id = $1 AND to_user_id = $2 AND is_deleted = FALSE AND role_type = 'ADMIN')`,
				invitation.CollectionID, invitation.InviterID).Scan(&admin); err != nil {
				return nil, err
			}
			if !admin {
				return nil, ente.ErrPermissionDenied
			}
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO collection_shares
			(collection_id, from_user_id, to_user_id, encrypted_key, updation_time, role_type, shared_at)
			VALUES ($1, $2, $3, $4, $5, $6, $7)
			ON CONFLICT (collection_id, from_user_id, to_user_id) DO UPDATE SET
				is_deleted = FALSE, updation_time = EXCLUDED.updation_time, role_type = EXCLUDED.role_type,
				encrypted_key = CASE WHEN collection_shares.is_deleted THEN EXCLUDED.encrypted_key ELSE collection_shares.encrypted_key END,
				shared_at = CASE WHEN collection_shares.is_deleted THEN EXCLUDED.shared_at ELSE collection_shares.shared_at END`,
			invitation.CollectionID, invitation.OwnerID, userID, invitation.EncryptedKey,
			time.Microseconds(), invitation.Role, invitation.SharedAt); err != nil {
			return nil, err
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO trusted_sharers (to_user_id, sharer_user_id)
			VALUES ($1, $2), ($1, $3) ON CONFLICT DO NOTHING`,
			userID, invitation.InviterID, invitation.OwnerID); err != nil {
			return nil, err
		}
	}
	if _, err := tx.ExecContext(ctx, `DELETE FROM collection_invitations WHERE invitation_id = ANY($1)`, pq.Array(ids)); err != nil {
		return nil, err
	}
	if _, err := tx.ExecContext(ctx, `UPDATE collections SET updation_time = GREATEST(updation_time + 1, $1)
		WHERE collection_id = ANY($2)`, time.Microseconds(), pq.Array(collectionIDs)); err != nil {
		return nil, err
	}
	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return collectionIDs, nil
}

func (repo *CollectionRepository) RejectInvitation(ctx context.Context, userID, invitationID int64) error {
	tx, err := repo.DB.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err := lockShareRecipients(ctx, tx, []int64{userID}, true); err != nil {
		return err
	}
	var collectionID int64
	err = tx.QueryRowContext(ctx, `DELETE FROM collection_invitations
		WHERE invitation_id = $1 AND to_user_id = $2 RETURNING collection_id`, invitationID, userID).Scan(&collectionID)
	if err == sql.ErrNoRows {
		return nil
	}
	if err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `UPDATE collections SET updation_time = GREATEST(updation_time + 1, $1)
		WHERE collection_id = $2`, time.Microseconds(), collectionID); err != nil {
		return err
	}
	return tx.Commit()
}

func sortedInvitationIDs(ids []int64) ([]int64, error) {
	if len(ids) == 0 {
		return nil, ente.ErrBadRequest
	}
	if len(ids) > ente.MaxCollectionShareBatchSize {
		return nil, ente.ErrBatchSizeTooLarge
	}
	result := append([]int64(nil), ids...)
	sort.Slice(result, func(i, j int) bool { return result[i] < result[j] })
	for i, id := range result {
		if id <= 0 || (i > 0 && result[i-1] == id) {
			return nil, ente.ErrBadRequest
		}
	}
	return result, nil
}

func validInvitationRole(role ente.CollectionParticipantRole) bool {
	return role == ente.VIEWER || role == ente.COLLABORATOR || role == ente.ADMIN
}
