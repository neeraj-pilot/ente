package controller

import (
	"database/sql"
	"testing"
	"time"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/ente/cache"
	"github.com/ente/museum/internal/testutil"
	"github.com/ente/museum/pkg/controller/usercache"
	"github.com/ente/museum/pkg/repo"
	storagebonusrepo "github.com/ente/museum/pkg/repo/storagebonus"
	"github.com/stretchr/testify/require"
)

func TestPhotoUploadFileLimit(t *testing.T) {
	for _, tc := range []struct {
		name            string
		photosFileCount int64
		app             ente.App
		paid            bool
		blocked         bool
	}{
		{name: "below limit", photosFileCount: 49_999, app: ente.Photos},
		{name: "at limit", photosFileCount: 50_000, app: ente.Photos, blocked: true},
		{name: "paid account", photosFileCount: 50_001, app: ente.Photos, paid: true},
		{name: "locker unaffected", photosFileCount: 50_000, app: ente.Locker},
	} {
		t.Run(tc.name, func(t *testing.T) {
			controller, db, userID := setupPhotoUploadLimitTest(t)
			_, err := db.Exec(`UPDATE usage SET photos_file_count = $2 WHERE user_id = $1`, userID, tc.photosFileCount)
			require.NoError(t, err)
			if tc.paid {
				_, err = db.Exec(`UPDATE subscriptions SET product_id = 'photos_yearly' WHERE user_id = $1`, userID)
				require.NoError(t, err)
			}
			err = controller.UsageCtrl.CanIssueUploadURLs(t.Context(), userID, nil, tc.app)
			if tc.blocked {
				require.ErrorIs(t, err, &ente.ErrFileLimitReached)
			} else {
				require.NoError(t, err)
			}
		})
	}
}

func TestPhotoUploadFileLimitEntitlements(t *testing.T) {
	for _, tc := range []struct {
		name       string
		productID  string
		bonusType  string
		family     bool
		selfHosted bool
		wantErr    error
	}{
		{name: "free referral bonus", bonusType: "REFERRAL", wantErr: &ente.ErrFileLimitReached},
		{name: "paid add-on", bonusType: "ADD_ON_SUPPORT"},
		{name: "paid family", productID: "photos_yearly", family: true},
		{name: "free family", family: true, wantErr: &ente.ErrFileLimitReached},
		{name: "self hosted", selfHosted: true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			controller, db, userID := setupPhotoUploadLimitTest(t)
			_, err := db.Exec(`UPDATE usage SET photos_file_count = 50000 WHERE user_id = $1`, userID)
			require.NoError(t, err)
			subscriptionUserID := userID
			if tc.family {
				subscriptionUserID = setupUploadLimitFamily(t, db, userID)
			}
			if tc.productID != "" {
				_, err = db.Exec(`UPDATE subscriptions SET product_id = $2 WHERE user_id = $1`, subscriptionUserID, tc.productID)
				require.NoError(t, err)
			}
			if tc.bonusType != "" {
				_, err = db.Exec(`INSERT INTO storage_bonus(bonus_id, user_id, storage, type, valid_till)
					VALUES('upload-limit-bonus', $1, $2, $3, $4)`, subscriptionUserID, 1<<30, tc.bonusType, time.Now().Add(time.Hour).UnixMicro())
				require.NoError(t, err)
			}
			if tc.selfHosted {
				_, err = db.Exec(`DELETE FROM users WHERE user_id = 1`)
				require.NoError(t, err)
			}
			err = controller.ValidateUploadEligibility(t.Context(), userID, ente.Photos)
			if tc.wantErr != nil {
				require.ErrorIs(t, err, tc.wantErr)
			} else {
				require.NoError(t, err)
			}
		})
	}
}

func TestPhotoUploadFileLimitSkipsUninitializedCounts(t *testing.T) {
	controller, db, userID := setupPhotoUploadLimitTest(t)
	_, err := db.Exec(`UPDATE usage SET photos_file_count = NULL, locker_file_count = NULL WHERE user_id = $1`, userID)
	require.NoError(t, err)
	controller.UsageCtrl.UsageRepo.QueueFileCountInitialization = func(int64) {
		t.Fatal("upload eligibility should not initialize file counts")
	}
	require.NoError(t, controller.ValidateUploadEligibility(t.Context(), userID, ente.Photos))
}

func TestPhotoUploadURLsRejectFileLimitDespiteCachedEligibility(t *testing.T) {
	controller, db, userID := setupPhotoUploadLimitTest(t)
	_, err := db.Exec(`UPDATE usage SET photos_file_count = 50000 WHERE user_id = $1`, userID)
	require.NoError(t, err)
	controller.UsageCtrl.UploadResultCache[userID] = true
	const checksum = "XUFAKrxLKna5cZ2REBfFkg=="
	for _, tc := range []struct {
		name    string
		request func() error
	}{
		{"legacy single", func() error {
			_, err := controller.GetUploadURLs(t.Context(), userID, 1, ente.Photos, false, "test")
			return err
		}},
		{"single", func() error {
			_, err := controller.GetUploadURLWithMetadata(t.Context(), userID,
				ente.UploadURLRequest{ContentLength: 5, ContentMD5: checksum}, ente.Photos, "test")
			return err
		}},
		{"legacy multipart", func() error {
			_, err := controller.GetMultipartUploadURLs(t.Context(), userID, 1, ente.Photos, "test")
			return err
		}},
		{"multipart", func() error {
			_, err := controller.GetMultipartUploadURLWithMetadata(t.Context(), userID,
				ente.MultipartUploadURLRequest{ContentLength: 5, PartLength: 5, PartMD5s: []string{checksum}}, ente.Photos, "test")
			return err
		}},
		{"eligibility", func() error {
			return controller.ValidateUploadEligibility(t.Context(), userID, ente.Photos)
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			require.ErrorIs(t, tc.request(), &ente.ErrFileLimitReached)
		})
	}
	delete(controller.UsageCtrl.UploadResultCache, userID)
	require.NoError(t, controller.UsageCtrl.CanUploadFile(t.Context(), userID, nil, ente.Photos))
}

func TestPhotoUploadEligibilityStorageLimits(t *testing.T) {
	for _, tc := range []struct {
		name         string
		userStorage  int64
		adminStorage int64
		blocked      bool
	}{
		{"within family and member limits", 54 << 20, 30 << 20, false},
		{"member limit exceeded", 56 << 20, 30 << 20, true},
		{"family limit exceeded", 54 << 20, 47 << 20, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			controller, db, userID := setupPhotoUploadLimitTest(t)
			adminID := setupUploadLimitFamily(t, db, userID)
			_, err := db.Exec(`UPDATE subscriptions SET storage = $2 WHERE user_id = $1`, adminID, 100<<20)
			require.NoError(t, err)
			_, err = db.Exec(`UPDATE families SET storage_limit = $2 WHERE member_id = $1`, userID, 5<<20)
			require.NoError(t, err)
			_, err = db.Exec(`UPDATE usage SET storage_consumed = $2 WHERE user_id = $1`, userID, tc.userStorage)
			require.NoError(t, err)
			testutil.InsertUsage(t, db, adminID, tc.adminStorage)
			err = controller.ValidateUploadEligibility(t.Context(), userID, ente.Photos)
			if tc.blocked {
				require.ErrorIs(t, err, ente.ErrStorageLimitExceeded)
			} else {
				require.NoError(t, err)
			}
		})
	}
}

func setupPhotoUploadLimitTest(t *testing.T) (*FileController, *sql.DB, int64) {
	t.Helper()
	db := testutil.RequireTestDB(t)
	testutil.ResetTables(t, db)
	t.Cleanup(func() { testutil.ResetTables(t, db) })
	testutil.InsertUser(t, db, testutil.UserFixture{UserID: 1, Email: "hosted-marker@ente.com", CreationTime: 1})
	userID := testutil.InsertUser(t, db, testutil.UserFixture{UserID: 10, Email: "upload-limit@ente.com", CreationTime: 1})
	_, err := db.Exec(`INSERT INTO usage(user_id, storage_consumed, photos_file_count, locker_file_count)
		VALUES($1, 0, 0, 0)`, userID)
	require.NoError(t, err)
	testutil.InsertSubscription(t, db, testutil.SubscriptionFixture{
		UserID: userID, Storage: 10 << 30, ExpiryTime: time.Now().Add(time.Hour).UnixMicro(), ProductID: ente.FreePlanProductID,
	})
	users := &repo.UserRepository{DB: db}
	bonuses := &storagebonusrepo.Repository{DB: db}
	return &FileController{UsageCtrl: &UsageController{
		UserRepo: users, UsageRepo: &repo.UsageRepository{DB: db},
		FamilyRepo: &repo.FamilyRepository{DB: db},
		BillingCtrl: &BillingController{
			UserRepo: users, BillingRepo: &repo.BillingRepository{DB: db}, StorageBonusRepo: bonuses,
		},
		UserCacheCtrl:     &usercache.Controller{UserCache: cache.NewUserCache(), StoreBonusRepo: bonuses},
		UploadResultCache: make(map[int64]bool),
	}}, db, userID
}

func setupUploadLimitFamily(t *testing.T, db *sql.DB, userID int64) int64 {
	t.Helper()
	adminID := testutil.InsertUser(t, db, testutil.UserFixture{UserID: 20, Email: "family-admin@ente.com", CreationTime: 1})
	_, err := db.Exec(`UPDATE users SET family_admin_id = $2 WHERE user_id = $1`, userID, adminID)
	require.NoError(t, err)
	_, err = db.Exec(`UPDATE subscriptions SET user_id = $2 WHERE user_id = $1`, userID, adminID)
	require.NoError(t, err)
	_, err = db.Exec(`INSERT INTO families(id, admin_id, member_id, status)
		VALUES(gen_random_uuid(), $1, $1, 'SELF'), (gen_random_uuid(), $1, $2, 'ACCEPTED')`, adminID, userID)
	require.NoError(t, err)
	return adminID
}
