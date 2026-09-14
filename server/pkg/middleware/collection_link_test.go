package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/internal/testutil"
	"github.com/ente/museum/pkg/controller"
	publicCtrl "github.com/ente/museum/pkg/controller/public"
	"github.com/ente/museum/pkg/repo"
	"github.com/ente/museum/pkg/repo/public"
	storagebonusrepo "github.com/ente/museum/pkg/repo/storagebonus"
	"github.com/ente/museum/pkg/utils/auth"
	timeutil "github.com/ente/museum/pkg/utils/time"
	"github.com/gin-gonic/gin"
	"github.com/patrickmn/go-cache"
	"github.com/stretchr/testify/require"
)

func TestCollectionLinkCacheIsScopedToOrigin(t *testing.T) {
	const (
		accessToken = "access-token"
		clientIP    = "192.0.2.1"
		userAgent   = "test-agent"
	)
	origins := []string{"https://albums.example", "https://gallery.example"}
	linkCache := cache.New(time.Minute, time.Minute)
	cacheVersion := public.LinkCacheVersion(linkCache, accessToken)
	for i, origin := range origins {
		key := computeHashKeyForList([]string{accessToken, clientIP, userAgent, origin, cacheVersion}, ":")
		linkCache.Set(key, ente.PublicCollectionSummary{ID: int64(i + 1)}, cache.DefaultExpiration)
	}

	gin.SetMode(gin.TestMode)
	var ids []int64
	router := gin.New()
	middleware := (&CollectionLinkMiddleware{Cache: linkCache}).Authenticate(func(c *gin.Context) string { return c.FullPath() })
	router.GET("/public-collection/files/download/:fileID", middleware, func(c *gin.Context) {
		ids = append(ids, auth.MustGetPublicAccessContext(c).ID)
		c.Status(http.StatusNoContent)
	})
	for _, origin := range origins {
		req := httptest.NewRequest(http.MethodGet, "/public-collection/files/download/1", nil)
		req.RemoteAddr = clientIP + ":1234"
		req.Header.Set("Origin", origin)
		req.Header.Set("User-Agent", userAgent)
		req.Header.Set("X-Auth-Access-Token", accessToken)
		resp := httptest.NewRecorder()
		router.ServeHTTP(resp, req)
		require.Equal(t, http.StatusNoContent, resp.Code)
	}
	require.Equal(t, []int64{1, 2}, ids)
}

func TestCollectionLinkMutationsTakeEffectImmediately(t *testing.T) {
	testutil.WithServerRoot(t)
	db := testutil.RequireTestDB(t)
	testutil.ResetTables(t, db)
	t.Cleanup(func() { testutil.ResetTables(t, db) })

	now := timeutil.Microseconds()
	ownerID := testutil.InsertUser(t, db, testutil.UserFixture{
		Email:        "collection-link-mutation@ente.io",
		CreationTime: now,
	})
	testutil.InsertSubscription(t, db, testutil.SubscriptionFixture{
		UserID:     ownerID,
		Storage:    1,
		ExpiryTime: now + timeutil.MicroSecondsInOneHour,
		ProductID:  "photos_yearly",
	})

	var collectionID int64
	err := db.QueryRow(`INSERT INTO collections
		(owner_id, encrypted_key, key_decryption_nonce, name, type, attributes, updation_time, app)
		VALUES ($1, 'key', 'nonce', 'Public collection', 'album', '{}', $2, $3)
		RETURNING collection_id`, ownerID, now, ente.Photos).Scan(&collectionID)
	require.NoError(t, err)
	const token = "public_collection_middleware_mutation"
	_, err = db.Exec(`INSERT INTO public_collection_tokens (collection_id, access_token)
		VALUES ($1, $2)`, collectionID, token)
	require.NoError(t, err)

	linkCache := cache.New(time.Minute, time.Minute)
	jwtSecret := []byte("collection-link-middleware-secret")
	collectionLinkRepo := public.NewCollectionLinkRepository(db, "")
	collectionLinkRepo.Cache = linkCache
	middleware := (&CollectionLinkMiddleware{
		CollectionLinkRepo: collectionLinkRepo,
		PublicCollectionCtrl: &publicCtrl.CollectionLinkController{
			JwtSecret: jwtSecret,
		},
		CollectionRepo: &repo.CollectionRepository{DB: db},
		Cache:          linkCache,
		BillingCtrl: &controller.BillingController{
			BillingRepo:      &repo.BillingRepository{DB: db},
			UserRepo:         &repo.UserRepository{DB: db},
			StorageBonusRepo: &storagebonusrepo.Repository{DB: db},
		},
	}).Authenticate(func(c *gin.Context) string { return c.FullPath() })
	router := gin.New()
	router.GET("/public-collection/files/download/:fileID", middleware,
		func(c *gin.Context) { c.Status(http.StatusNoContent) })
	request := func(passwordJWT string) int {
		req := httptest.NewRequest(http.MethodGet, "/public-collection/files/download/1", nil)
		req.RemoteAddr = "192.0.2.1:1234"
		req.Header.Set("User-Agent", "test-agent")
		req.Header.Set("X-Auth-Access-Token", token)
		if passwordJWT != "" {
			req.Header.Set("X-Auth-Access-Token-JWT", passwordJWT)
		}
		response := httptest.NewRecorder()
		router.ServeHTTP(response, req)
		return response.Code
	}
	requireWarmCache := func() {
		key := computeHashKeyForList([]string{
			token,
			"192.0.2.1",
			"test-agent",
			"",
			public.LinkCacheVersion(linkCache, token),
		}, ":")
		_, found := linkCache.Get(key)
		require.True(t, found)
	}

	require.Equal(t, http.StatusNoContent, request(""))
	requireWarmCache()
	collectionLinkRow, err := collectionLinkRepo.GetActiveCollectionLinkRow(t.Context(), collectionID)
	require.NoError(t, err)
	collectionLinkRow.ValidTill = 1
	require.NoError(t, collectionLinkRepo.UpdatePublicCollectionToken(t.Context(), collectionLinkRow))
	require.Equal(t, http.StatusGone, request(""))
	collectionLinkRow.ValidTill = 0
	require.NoError(t, collectionLinkRepo.UpdatePublicCollectionToken(t.Context(), collectionLinkRow))
	require.Equal(t, http.StatusNoContent, request(""))
	requireWarmCache()

	oldPassHash, nonce := "old-hash", "nonce"
	collectionLinkRow.PassHash = &oldPassHash
	collectionLinkRow.Nonce = &nonce
	require.NoError(t, collectionLinkRepo.UpdatePublicCollectionToken(t.Context(), collectionLinkRow))
	require.Equal(t, http.StatusUnauthorized, request(""))
	oldPasswordJWT := linkPasswordJWT(t, jwtSecret, oldPassHash)
	require.Equal(t, http.StatusNoContent, request(oldPasswordJWT))
	requireWarmCache()

	newPassHash := "new-hash"
	collectionLinkRow.PassHash = &newPassHash
	require.NoError(t, collectionLinkRepo.UpdatePublicCollectionToken(t.Context(), collectionLinkRow))
	require.Equal(t, http.StatusUnauthorized, request(oldPasswordJWT))
	newPasswordJWT := linkPasswordJWT(t, jwtSecret, newPassHash)
	require.Equal(t, http.StatusNoContent, request(newPasswordJWT))
	requireWarmCache()

	require.NoError(t, collectionLinkRepo.DisableSharing(t.Context(), collectionID))
	require.Equal(t, http.StatusGone, request(newPasswordJWT))
}
