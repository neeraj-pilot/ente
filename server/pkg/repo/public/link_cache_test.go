package public

import (
	"testing"
	"time"

	"github.com/patrickmn/go-cache"
)

func TestLinkCacheVersionReplacesMissingMarker(t *testing.T) {
	const (
		accessToken = "reclaimed-token"
		oldVersion  = "old-version"
	)
	key := linkCacheVersionKey(accessToken)

	for _, tc := range []struct {
		name       string
		expiration int64
	}{
		{"expired", 1},
		{"evicted", time.Now().Add(time.Minute).UnixNano()},
	} {
		t.Run(tc.name, func(t *testing.T) {
			linkCache := cache.NewFrom(time.Minute, 0, map[string]cache.Item{
				key: {Object: oldVersion, Expiration: tc.expiration},
			})
			if tc.name == "expired" {
				linkCache.DeleteExpired()
			} else {
				linkCache.Delete(key)
			}
			linkCache.Set("row:"+oldVersion, "stale", cache.DefaultExpiration)

			version := LinkCacheVersion(linkCache, accessToken)
			if version == "" || version == oldVersion {
				t.Fatalf("got reclaimed version %q", version)
			}
			if _, found := linkCache.Get("row:" + version); found {
				t.Fatal("reclaimed marker reused stale generation")
			}
			_, expiration, found := linkCache.GetWithExpiration(key)
			if !found || expiration.IsZero() {
				t.Fatal("reclaimed marker does not expire")
			}
		})
	}
}

func TestInvalidateLinkCacheMarkerExpires(t *testing.T) {
	linkCache := cache.New(time.Minute, 0)
	InvalidateLinkCache(linkCache, "invalidated-token")

	_, expiration, found := linkCache.GetWithExpiration(linkCacheVersionKey("invalidated-token"))
	if !found || expiration.IsZero() {
		t.Fatal("invalidation marker does not expire")
	}
}

func TestStaleLinkCacheFillDoesNotBecomeCurrent(t *testing.T) {
	const accessToken = "racing-token"
	linkCache := cache.New(time.Minute, 0)
	version := LinkCacheVersion(linkCache, accessToken)
	cacheKey := "row:" + version

	InvalidateLinkCache(linkCache, accessToken)
	SetLinkCacheValue(linkCache, accessToken, cacheKey, version, "stale")

	if _, found := linkCache.Get(cacheKey); found {
		t.Fatal("stale cache fill survived generation check")
	}
}

func TestLinkCacheVersionWithoutCache(t *testing.T) {
	if LinkCacheVersion(nil, "token") != "" {
		t.Fatal("nil cache returned a version")
	}
	InvalidateLinkCache(nil, "token")
}
