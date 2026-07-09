package controller

import (
	"fmt"
	"strings"

	baserepo "github.com/ente/museum/pkg/repo"
	emailutil "github.com/ente/museum/pkg/utils/email"
	log "github.com/sirupsen/logrus"
	"github.com/spf13/viper"
)

const (
	spaceNotificationTemplate          = "space_notification.html"
	spaceNewPostIllustrationURL        = "https://email-assets.ente.com/space-new-post.png"
	spaceNewPostLikeIllustrationURL    = "https://email-assets.ente.com/space-new-post-like.png"
	spaceNewPostReplyIllustrationURL   = "https://email-assets.ente.com/space-new-post-reply.png"
	spaceNewFriendIllustrationURL      = "https://email-assets.ente.com/space-new-friend.png"
	spaceNotificationPostCreated       = "post_created"
	spaceNotificationPostLiked         = "post_liked"
	spaceNotificationPostReplied       = "post_replied"
	spaceNotificationFriendAdded       = "friend_added"
	spaceNotificationFriendRequested   = "friend_requested"
	spaceNewPostIllustrationWidth      = 112
	spaceNewPostLikeIllustrationWidth  = 132
	spaceNewPostReplyIllustrationWidth = 132
	spaceNewFriendIllustrationWidth    = 220
)

var sendSpaceNotificationEmail = emailutil.SendTemplatedEmail

type SpaceEmailNotifier interface {
	OnSpacePostCreated(authorSlug string, recipientUserIDs []int64)
	OnSpacePostLiked(actorSlug string, recipientUserID int64)
	OnSpacePostReplied(actorSlug string, recipientUserID int64)
	OnSpaceFriendAdded(actorSlug string, recipientUserID int64)
	OnSpaceFriendRequested(actorSlug string, recipientUserID int64)
}

type SpaceEmailSender struct {
	UserRepo *baserepo.UserRepository
}

func (n *SpaceEmailSender) OnSpacePostCreated(authorSlug string, recipientUserIDs []int64) {
	n.send(authorSlug, "posted a new photo", spaceNotificationPostCreated, recipientUserIDs)
}

func (n *SpaceEmailSender) OnSpacePostLiked(actorSlug string, recipientUserID int64) {
	n.send(actorSlug, "liked your post", spaceNotificationPostLiked, []int64{recipientUserID})
}

func (n *SpaceEmailSender) OnSpacePostReplied(actorSlug string, recipientUserID int64) {
	n.send(actorSlug, "replied to your post", spaceNotificationPostReplied, []int64{recipientUserID})
}

func (n *SpaceEmailSender) OnSpaceFriendAdded(actorSlug string, recipientUserID int64) {
	n.send(actorSlug, "is now your friend", spaceNotificationFriendAdded, []int64{recipientUserID})
}

func (n *SpaceEmailSender) OnSpaceFriendRequested(actorSlug string, recipientUserID int64) {
	n.send(actorSlug, "sent you a friend request", spaceNotificationFriendRequested, []int64{recipientUserID})
}

func (n *SpaceEmailSender) send(actorSlug, action, event string, recipientUserIDs []int64) {
	if n == nil || n.UserRepo == nil || len(recipientUserIDs) == 0 {
		return
	}
	recipientUserIDs = uniqueUserIDs(recipientUserIDs)
	if len(recipientUserIDs) == 0 {
		return
	}
	users, err := n.UserRepo.GetActiveUsersForIds(recipientUserIDs)
	if err != nil {
		log.WithField("event", event).WithError(err).Error("Error fetching users for space email")
		return
	}

	subject := spaceEmailSubject(actorSlug, action)
	templateData := map[string]interface{}{
		"ActorLabel":        spaceEmailActorLabel(actorSlug),
		"AppURL":            spaceAppURL(),
		"IllustrationURL":   spaceEmailIllustrationURL(event),
		"IllustrationWidth": spaceEmailIllustrationWidth(event),
		"Notification":      spaceEmailNotificationText(event, action),
	}
	for _, userID := range recipientUserIDs {
		user := users[userID]
		if user == nil || strings.TrimSpace(user.Email) == "" {
			continue
		}
		if err := sendSpaceNotificationEmail(
			[]string{user.Email},
			"Ente",
			"team@ente.com",
			subject,
			spaceNotificationTemplate,
			templateData,
			nil,
		); err != nil {
			log.WithFields(log.Fields{
				"user_id": userID,
				"email":   user.Email,
				"event":   event,
			}).WithError(err).Error("Error sending space email")
		}
	}
}

func spaceEmailSubject(actorSlug, action string) string {
	actorSlug = strings.TrimSpace(actorSlug)
	if actorSlug == "" {
		return fmt.Sprintf("A friend %s", action)
	}
	return fmt.Sprintf("@%s %s", actorSlug, action)
}

func spaceEmailActorLabel(actorSlug string) string {
	actorSlug = strings.TrimSpace(actorSlug)
	if actorSlug == "" {
		return "A friend"
	}
	return fmt.Sprintf("@%s", actorSlug)
}

func spaceEmailNotificationText(event, action string) string {
	switch event {
	case spaceNotificationPostCreated:
		return "just posted a new photo"
	case spaceNotificationPostLiked:
		return "just liked your post"
	case spaceNotificationPostReplied:
		return "just replied to your post"
	case spaceNotificationFriendAdded:
		return "is now your friend"
	case spaceNotificationFriendRequested:
		return "sent you a friend request"
	default:
		return action
	}
}

func spaceEmailIllustrationURL(event string) string {
	switch event {
	case spaceNotificationPostCreated:
		return spaceNewPostIllustrationURL
	case spaceNotificationPostLiked:
		return spaceNewPostLikeIllustrationURL
	case spaceNotificationPostReplied:
		return spaceNewPostReplyIllustrationURL
	case spaceNotificationFriendAdded:
		return spaceNewFriendIllustrationURL
	case spaceNotificationFriendRequested:
		return spaceNewFriendIllustrationURL
	default:
		return ""
	}
}

func spaceEmailIllustrationWidth(event string) int {
	switch event {
	case spaceNotificationPostCreated:
		return spaceNewPostIllustrationWidth
	case spaceNotificationPostLiked:
		return spaceNewPostLikeIllustrationWidth
	case spaceNotificationPostReplied:
		return spaceNewPostReplyIllustrationWidth
	case spaceNotificationFriendAdded:
		return spaceNewFriendIllustrationWidth
	case spaceNotificationFriendRequested:
		return spaceNewFriendIllustrationWidth
	default:
		return 0
	}
}

func uniqueUserIDs(userIDs []int64) []int64 {
	seen := make(map[int64]struct{}, len(userIDs))
	out := make([]int64, 0, len(userIDs))
	for _, userID := range userIDs {
		if userID <= 0 {
			continue
		}
		if _, ok := seen[userID]; ok {
			continue
		}
		seen[userID] = struct{}{}
		out = append(out, userID)
	}
	return out
}

func spaceAppURL() string {
	origin := strings.TrimRight(strings.TrimSpace(viper.GetString("apps.space")), "/")
	if origin == "" {
		origin = "https://ente.space"
	}
	return origin + "/app"
}
