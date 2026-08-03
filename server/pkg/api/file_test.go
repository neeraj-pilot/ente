package api

import (
	"database/sql"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/ente/stacktrace"
	"github.com/gin-gonic/gin"

	"github.com/ente/museum/ente"
	apiHandler "github.com/ente/museum/pkg/utils/handler"
)

func TestFileURLV3MissingObjectReturnsBadRequest(t *testing.T) {
	gin.SetMode(gin.TestMode)
	recorder := httptest.NewRecorder()
	ctx, _ := gin.CreateTestContext(recorder)
	ctx.Request = httptest.NewRequest(http.MethodGet, "/files/download/v3/1", nil)

	apiHandler.Error(ctx, stacktrace.Propagate(fileURLV3Error(sql.ErrNoRows), ""))

	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusBadRequest)
	}
	var response ente.ApiError
	if err := json.Unmarshal(recorder.Body.Bytes(), &response); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if response.Code != ente.NotFoundError {
		t.Fatalf("code = %q, want %q", response.Code, ente.NotFoundError)
	}
}
