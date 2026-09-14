package public

import (
	"strconv"
	"sync/atomic"
	"time"

	"github.com/patrickmn/go-cache"
)

type LinkCache struct {
	*cache.Cache
	version atomic.Uint64
}

func NewLinkCache(defaultExpiration, cleanupInterval time.Duration) *LinkCache {
	return &LinkCache{Cache: cache.New(defaultExpiration, cleanupInterval)}
}

func (c *LinkCache) Version() string {
	if c == nil {
		return ""
	}
	return strconv.FormatUint(c.version.Load(), 10)
}

func (c *LinkCache) Invalidate() {
	if c != nil {
		c.version.Add(1)
	}
}

func (c *LinkCache) SetIfCurrent(key, version string, value any) {
	c.Set(key, value, cache.DefaultExpiration)
	if c.Version() != version {
		c.Delete(key)
	}
}
