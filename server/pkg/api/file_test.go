package api

import (
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ente/stacktrace"
	"github.com/gin-gonic/gin"

	"github.com/ente/museum/ente"
)

func TestWriteFileURLV3ReturnsJSONWithoutRedirect(t *testing.T) {
	recorder, ctx := newFileURLV3TestContext("/files/download/v3/1")

	writeFileURLV3(ctx, "https://objects.example/file", nil)

	if recorder.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusOK)
	}
	if location := recorder.Header().Get("Location"); location != "" {
		t.Fatalf("Location = %q, want empty", location)
	}
	var response struct {
		URL string `json:"url"`
	}
	if err := json.Unmarshal(recorder.Body.Bytes(), &response); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if response.URL != "https://objects.example/file" {
		t.Fatalf("url = %q, want %q", response.URL, "https://objects.example/file")
	}
}

func TestWriteFileURLV3MapsNotFoundErrorsToBadRequest(t *testing.T) {
	tests := []struct {
		name string
		err  error
	}{
		{name: "sql no rows", err: stacktrace.Propagate(sql.ErrNoRows, "lookup object")},
		{name: "not found sentinel", err: stacktrace.Propagate(ente.ErrNotFound, "lookup memory file")},
		{name: "not found API error", err: stacktrace.Propagate(ente.ErrNotFoundError.NewErr("missing object"), "lookup file")},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			recorder, ctx := newFileURLV3TestContext("/files/download/v3/1")

			writeFileURLV3(ctx, "", test.err)

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
		})
	}
}

func TestWriteFileURLV3PreservesNonNotFoundErrors(t *testing.T) {
	tests := []struct {
		name       string
		err        error
		wantStatus int
	}{
		{name: "bad request", err: ente.ErrBadRequest, wantStatus: http.StatusBadRequest},
		{name: "permission denied", err: ente.ErrPermissionDenied, wantStatus: http.StatusForbidden},
		{name: "internal error", err: errors.New("storage unavailable"), wantStatus: http.StatusInternalServerError},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			recorder, ctx := newFileURLV3TestContext("/files/download/v3/1")

			writeFileURLV3(ctx, "", test.err)

			if recorder.Code != test.wantStatus {
				t.Fatalf("status = %d, want %d", recorder.Code, test.wantStatus)
			}
			if recorder.Code == http.StatusNotFound {
				t.Fatal("HTTP 404 is reserved for an unavailable v3 route")
			}
		})
	}
}

func TestPublicCollectionFileURLV3InvalidFileIDReturnsBadRequest(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.GET("/public-collection/files/download/v3/:fileID", (&PublicCollectionHandler{}).GetFileURLV3)
	recorder := httptest.NewRecorder()

	router.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/public-collection/files/download/v3/invalid", nil))

	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusBadRequest)
	}
}

func TestFileWriteHandlersRejectClientUpdationTime(t *testing.T) {
	gin.SetMode(gin.TestMode)
	fileHandler := &FileHandler{}
	publicHandler := &PublicCollectionHandler{}
	router := gin.New()
	router.POST("/files", fileHandler.CreateOrUpdate)
	router.POST("/files/meta", fileHandler.CreateMetaFile)
	router.PUT("/files/update", fileHandler.Update)
	router.POST("/public-collection/file", publicHandler.CreateFile)

	const attrs = `"file":{"objectKey":"1/file","decryptionHeader":"f-hdr"},"thumbnail":{"objectKey":"1/thumb","decryptionHeader":"t-hdr"},"metadata":{"encryptedData":"meta","decryptionHeader":"m-hdr"}`
	endpoints := []struct {
		method string
		path   string
		body   func(updationTime int64) string
	}{
		{
			method: http.MethodPost,
			path:   "/files",
			body: func(updationTime int64) string {
				return fmt.Sprintf(`{"collectionID":1,%s,"updationTime":%d}`, attrs, updationTime)
			},
		},
		{
			method: http.MethodPost,
			path:   "/files/meta",
			body: func(updationTime int64) string {
				return fmt.Sprintf(`{"collectionID":1,"metadata":{"encryptedData":"meta","decryptionHeader":"m-hdr"},"updationTime":%d}`, updationTime)
			},
		},
		{
			method: http.MethodPut,
			path:   "/files/update",
			body: func(updationTime int64) string {
				return fmt.Sprintf(`{"id":1,%s,"updationTime":%d}`, attrs, updationTime)
			},
		},
		{
			method: http.MethodPost,
			path:   "/public-collection/file",
			body: func(updationTime int64) string {
				return fmt.Sprintf(`{"collectionID":1,%s,"updationTime":%d}`, attrs, updationTime)
			},
		},
	}

	for _, endpoint := range endpoints {
		for _, updationTime := range []int64{0, 123} {
			t.Run(fmt.Sprintf("%s_%s_%d", endpoint.method, endpoint.path, updationTime), func(t *testing.T) {
				req := httptest.NewRequest(endpoint.method, endpoint.path, strings.NewReader(endpoint.body(updationTime)))
				req.Header.Set("Content-Type", "application/json")
				recorder := httptest.NewRecorder()
				router.ServeHTTP(recorder, req)
				if recorder.Code != http.StatusBadRequest {
					t.Fatalf("status = %d, want %d (body: %s)", recorder.Code, http.StatusBadRequest, recorder.Body.String())
				}
			})
		}
	}
}

func TestFileWriteRequestsIgnoreServerOwnedFields(t *testing.T) {
	payload := `{
		"collectionID": 7,
		"ownerID": 999,
		"collectionOwnerID": 888,
		"collectionAddedAt": 777,
		"isDeleted": true,
		"info": {"fileSize": 999999},
		"action": "ADD",
		"actionUser": 666,
		"file": {"objectKey": "42/file", "decryptionHeader": "f-hdr"},
		"thumbnail": {"objectKey": "42/thumb", "decryptionHeader": "t-hdr"},
		"metadata": {"encryptedData": "meta", "decryptionHeader": "m-hdr"}
	}`

	var fileReq fileWriteRequest
	if err := json.Unmarshal([]byte(payload), &fileReq); err != nil {
		t.Fatalf("unmarshal fileWriteRequest: %v", err)
	}
	file, err := fileReq.toFile(42)
	if err != nil {
		t.Fatalf("toFile returned error: %v", err)
	}
	if file.OwnerID != 42 {
		t.Fatalf("file.OwnerID = %d, want 42", file.OwnerID)
	}
	if file.IsDeleted {
		t.Fatal("file.IsDeleted = true, want false")
	}
	if file.CollectionOwnerID != nil || file.CollectionAddedAt != nil || file.Info != nil || file.Action != nil || file.ActionUserID != nil {
		t.Fatalf("server-owned fields populated on file: %+v", file)
	}
	if file.UpdationTime <= 0 {
		t.Fatalf("file.UpdationTime = %d, want > 0", file.UpdationTime)
	}

	var metaReq metaFileCreateRequest
	if err := json.Unmarshal([]byte(payload), &metaReq); err != nil {
		t.Fatalf("unmarshal metaFileCreateRequest: %v", err)
	}
	metaFile, err := metaReq.toMetaFile(42)
	if err != nil {
		t.Fatalf("toMetaFile returned error: %v", err)
	}
	if metaFile.OwnerID != 42 {
		t.Fatalf("metaFile.OwnerID = %d, want 42", metaFile.OwnerID)
	}
	if metaFile.IsDeleted {
		t.Fatal("metaFile.IsDeleted = true, want false")
	}
	if metaFile.UpdationTime <= 0 {
		t.Fatalf("metaFile.UpdationTime = %d, want > 0", metaFile.UpdationTime)
	}
}

func newFileURLV3TestContext(path string) (*httptest.ResponseRecorder, *gin.Context) {
	gin.SetMode(gin.TestMode)
	recorder := httptest.NewRecorder()
	ctx, _ := gin.CreateTestContext(recorder)
	ctx.Request = httptest.NewRequest(http.MethodGet, path, nil)
	return recorder, ctx
}
