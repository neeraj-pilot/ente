package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/ente/museum/internal/testutil"
	"github.com/ente/museum/pkg/repo/public"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/require"
)

func TestFileLinkGoneResponsePreservesLegacyError(t *testing.T) {
	testutil.WithServerRoot(t)
	db := testutil.RequireTestDB(t)

	for _, tc := range []struct {
		name     string
		disabled bool
		body     string
	}{
		{"expired", false, `{"code":"LINK_EXPIRED","error":"expired token"}`},
		{"disabled", true, `{"code":"LINK_DISABLED","error":"disabled token"}`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			token := "pft_middleware_" + tc.name
			_, err := db.Exec(`INSERT INTO public_file_tokens
				(id, file_id, owner_id, app, access_token, is_disabled, valid_till)
				VALUES ($1, 1, 1, 'photos', $1, $2, 1)`, token, tc.disabled)
			require.NoError(t, err)
			t.Cleanup(func() {
				_, err := db.Exec("DELETE FROM public_file_tokens WHERE id = $1", token)
				require.NoError(t, err)
			})

			middleware := &FileLinkMiddleware{FileLinkRepo: public.NewFileLinkRepo(db)}
			router := gin.New()
			router.GET("/file-link/info", middleware.Authenticate(func(c *gin.Context) string {
				return c.FullPath()
			}), func(c *gin.Context) { c.Status(http.StatusNoContent) })
			req := httptest.NewRequest(http.MethodGet, "/file-link/info", nil)
			req.Header.Set("X-Auth-Access-Token", token)
			response := httptest.NewRecorder()
			router.ServeHTTP(response, req)
			require.Equal(t, http.StatusGone, response.Code)
			require.JSONEq(t, tc.body, response.Body.String())
		})
	}
}
