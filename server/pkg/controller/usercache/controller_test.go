package usercache

import (
	"context"
	"testing"
	"time"

	"github.com/ente/museum/ente/cache"
	bonus "github.com/ente/museum/ente/storagebonus"
	"github.com/ente/museum/internal/testutil"
	"github.com/ente/museum/pkg/repo/storagebonus"
	"github.com/stretchr/testify/require"
)

func TestGetActiveStorageBonusRefreshSurvivesRequestCancellation(t *testing.T) {
	db := testutil.RequireTestDB(t)
	testutil.ResetTables(t, db)
	t.Cleanup(func() { testutil.ResetTables(t, db) })

	userID := testutil.InsertUser(t, db, testutil.UserFixture{Email: "bonus-cache@ente.io", CreationTime: 1})
	_, err := db.Exec(`INSERT INTO storage_bonus (bonus_id, user_id, storage, type, valid_till) VALUES ('signup-1', $1, 1024, 'SIGN_UP', 0)`, userID)
	require.NoError(t, err)

	userCache := cache.NewUserCache()
	userCache.SetBonus(userID, &bonus.ActiveStorageBonus{})
	controller := &Controller{
		StoreBonusRepo: storagebonus.NewRepository(db),
		UserCache:      userCache,
	}

	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	got, err := controller.GetActiveStorageBonus(ctx, userID)
	require.NoError(t, err)
	require.Empty(t, got.StorageBonuses)

	require.Eventually(t, func() bool {
		cached, ok := userCache.GetBonus(userID)
		return ok && len(cached.StorageBonuses) == 1 && cached.StorageBonuses[0].Storage == 1024
	}, 5*time.Second, 10*time.Millisecond)
}
