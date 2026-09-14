package public

import (
	"crypto/sha256"
	"encoding/hex"

	"github.com/google/uuid"
	"github.com/patrickmn/go-cache"
)

const linkCacheVersionPrefix = "public-link-version:"

func LinkCacheVersion(linkCache *cache.Cache, accessToken string) string {
	if linkCache == nil {
		return ""
	}
	key := linkCacheVersionKey(accessToken)
	for {
		if version, found := linkCache.Get(key); found {
			return version.(string)
		}
		version := uuid.NewString()
		if linkCache.Add(key, version, cache.DefaultExpiration) == nil {
			return version
		}
	}
}

func InvalidateLinkCache(linkCache *cache.Cache, accessTokens ...string) {
	if linkCache == nil || len(accessTokens) == 0 {
		return
	}
	version := uuid.NewString()
	for _, accessToken := range accessTokens {
		linkCache.Set(linkCacheVersionKey(accessToken), version, cache.DefaultExpiration)
	}
}

func SetLinkCacheValue(linkCache *cache.Cache, accessToken, cacheKey, version string, value interface{}) {
	linkCache.Set(cacheKey, value, cache.DefaultExpiration)
	if LinkCacheVersion(linkCache, accessToken) != version {
		linkCache.Delete(cacheKey)
	}
}

func linkCacheVersionKey(accessToken string) string {
	hash := sha256.Sum256([]byte(accessToken))
	return linkCacheVersionPrefix + hex.EncodeToString(hash[:])
}
