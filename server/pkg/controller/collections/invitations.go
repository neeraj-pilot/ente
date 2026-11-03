package collections

import (
	"context"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/pkg/utils/auth"
	"github.com/gin-gonic/gin"
)

func (c *CollectionController) HasPendingInvitations(ctx context.Context, userID int64, app ente.App) (bool, error) {
	if app != ente.Photos {
		return false, nil
	}
	return c.CollectionRepo.HasPendingInvitations(ctx, userID)
}

func (c *CollectionController) ListPendingInvitations(ctx *gin.Context, beforeID int64, limit int) ([]ente.CollectionInvitation, bool, error) {
	if auth.GetApp(ctx) != ente.Photos {
		return nil, false, ente.ErrPermissionDenied
	}
	return c.CollectionRepo.ListPendingInvitations(ctx.Request.Context(), auth.GetUserID(ctx.Request.Header), beforeID, limit)
}

func (c *CollectionController) AcceptInvitations(ctx *gin.Context, request ente.AcceptCollectionInvitationsRequest) ([]int64, error) {
	if auth.GetApp(ctx) != ente.Photos {
		return nil, ente.ErrPermissionDenied
	}
	return c.CollectionRepo.AcceptInvitations(ctx.Request.Context(), auth.GetUserID(ctx.Request.Header), request.InvitationIDs)
}

func (c *CollectionController) RejectInvitation(ctx *gin.Context, request ente.RejectCollectionInvitationRequest) error {
	if auth.GetApp(ctx) != ente.Photos {
		return ente.ErrPermissionDenied
	}
	return c.CollectionRepo.RejectInvitation(ctx.Request.Context(), auth.GetUserID(ctx.Request.Header), request.InvitationID)
}
