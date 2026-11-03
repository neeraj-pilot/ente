package api

import (
	"net/http"
	"strconv"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/pkg/utils/handler"
	"github.com/gin-gonic/gin"
)

func (h *CollectionHandler) ListInvitations(c *gin.Context) {
	limit := 50
	if value := c.Query("limit"); value != "" {
		parsed, err := strconv.Atoi(value)
		if err != nil || parsed < 1 || parsed > 100 {
			handler.Error(c, ente.ErrBadRequest)
			return
		}
		limit = parsed
	}
	var beforeID int64
	if value := c.Query("beforeID"); value != "" {
		parsed, err := strconv.ParseInt(value, 10, 64)
		if err != nil || parsed <= 0 {
			handler.Error(c, ente.ErrBadRequest)
			return
		}
		beforeID = parsed
	}
	invitations, hasMore, err := h.Controller.ListPendingInvitations(c, beforeID, limit)
	if err != nil {
		handler.Error(c, err)
		return
	}
	c.JSON(http.StatusOK, gin.H{"invitations": invitations, "hasMore": hasMore})
}

func (h *CollectionHandler) AcceptInvitations(c *gin.Context) {
	var request ente.AcceptCollectionInvitationsRequest
	if err := handler.BindJSON(c, &request); err != nil {
		handler.Error(c, err)
		return
	}
	acceptedCollectionIDs, err := h.Controller.AcceptInvitations(c, request)
	if err != nil {
		handler.Error(c, err)
		return
	}
	c.JSON(http.StatusOK, gin.H{"acceptedCollectionIDs": acceptedCollectionIDs})
}

func (h *CollectionHandler) RejectInvitation(c *gin.Context) {
	var request ente.RejectCollectionInvitationRequest
	if err := handler.BindJSON(c, &request); err != nil {
		handler.Error(c, err)
		return
	}
	if err := h.Controller.RejectInvitation(c, request); err != nil {
		handler.Error(c, err)
		return
	}
	c.JSON(http.StatusOK, gin.H{})
}
