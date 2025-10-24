package auth

import (
	"crypto/rand"
	"encoding/base64"
	"github.com/ente-io/museum/ente/cast"
	"github.com/sirupsen/logrus"
	"math/big"
	"net/http"
	"strconv"
	"strings"

	"github.com/ente-io/museum/ente"
	"github.com/ente-io/stacktrace"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

const (
	PublicAccessKey   = "X-Public-Access-ID"
	FileLinkAccessKey = "X-Public-FileLink-Access-ID"
	CastContext       = "X-Cast-Context"
	UserIDContextKey  = "X-Auth-User-ID-Context"
)

// GenerateRandomBytes returns securely generated random bytes.
// It will return an error if the system's secure random
// number generator fails to function correctly, in which
// case the caller should not continue.
func GenerateRandomBytes(n int) ([]byte, error) {
	b := make([]byte, n)
	_, err := rand.Read(b)
	// Note that err == nil only if we read len(b) bytes.
	if err != nil {
		return nil, stacktrace.Propagate(err, "")
	}

	return b, nil
}

// GenerateRandomInt returns a securely generated random integer in [0, n).
//
// It will return an error if the system's secure random number generator fails
// to function correctly, in which case the caller should not continue.
func GenerateRandomInt(n int64) (int64, error) {
	r, err := rand.Int(rand.Reader, big.NewInt(n))
	if err != nil {
		return 0, stacktrace.Propagate(err, "")
	}
	return r.Int64(), nil
}

// GenerateURLSafeRandomString returns a URL-safe, base64 encoded
// securely generated random string.
// It will return an error if the system's secure random
// number generator fails to function correctly, in which
// case the caller should not continue.
func GenerateURLSafeRandomString(s int) (string, error) {
	b, err := GenerateRandomBytes(s)
	return base64.URLEncoding.EncodeToString(b), stacktrace.Propagate(err, "")
}

// GetHashedPassword returns the has of a specified password
func GetHashedPassword(password string) (string, error) {
	saltedBytes := []byte(password)
	hashedBytes, err := bcrypt.GenerateFromPassword(saltedBytes, bcrypt.DefaultCost)
	if err != nil {
		return "", stacktrace.Propagate(err, "")
	}

	hash := string(hashedBytes[:])
	return hash, nil
}

// CompareHashes compares a bcrypt hashed password with its possible plaintext
// equivalent. Returns nil on success, or an error on failure.
func CompareHashes(hash string, s string) error {
	existing := []byte(hash)
	incoming := []byte(s)
	return bcrypt.CompareHashAndPassword(existing, incoming)
}

// GetUserID fetches the userID from context and validates against header
// If context value is missing, logs warning and uses header value
// If values differ, returns an error
func GetUserID(c *gin.Context) int64 {
	// Get userID from context
	contextUserID, exists := c.Get(UserIDContextKey)

	// Get userID from header
	headerUserID, _ := strconv.ParseInt(c.Request.Header.Get("X-Auth-User-ID"), 10, 64)

	if !exists {
		// Context value not present, log and use header value
		logrus.WithField("header_user_id", headerUserID).
			Warn("User ID not found in context, falling back to header value")
		return headerUserID
	}

	// Convert context value to int64
	contextUserIDInt64, ok := contextUserID.(int64)
	if !ok {
		logrus.WithField("context_user_id", contextUserID).
			Error("User ID in context is not int64")
		return 0
	}

	// Compare context and header values
	if contextUserIDInt64 != headerUserID {
		logrus.WithFields(logrus.Fields{
			"context_user_id": contextUserIDInt64,
			"header_user_id":  headerUserID,
		}).Error("User ID mismatch between context and header")
		return 0
	}

	return contextUserIDInt64
}

// GetUserIDFromContext fetches the userID from the Gin context
func GetUserIDFromContext(c *gin.Context) (int64, bool) {
	userID, exists := c.Get(UserIDContextKey)
	if !exists {
		return 0, false
	}
	userIDInt64, ok := userID.(int64)
	if !ok {
		return 0, false
	}
	return userIDInt64, true
}

// GetUserIDFromHeader fetches the userID embedded in a request header
// This is a legacy function for backward compatibility
func GetUserIDFromHeader(header http.Header) int64 {
	userID, _ := strconv.ParseInt(header.Get("X-Auth-User-ID"), 10, 64)
	return userID
}

func GetApp(c *gin.Context) ente.App {
	if strings.HasPrefix(c.GetHeader("X-Client-Package"), "io.ente.auth") {
		return ente.Auth
	}

	if strings.HasPrefix(c.GetHeader("X-Client-Package"), "io.ente.locker") {
		return ente.Locker
	}

	return ente.Photos
}

func GetToken(c *gin.Context) string {
	token := c.GetHeader("X-Auth-Token")
	if token == "" {
		token = c.Query("token")
	}
	return token
}

func GetAccessToken(c *gin.Context) string {
	token := c.GetHeader("X-Auth-Access-Token")
	if token == "" {
		token = c.Query("accessToken")
	}
	return token
}

func GetCastToken(c *gin.Context) string {
	token := c.GetHeader("X-Cast-Access-Token")
	if token == "" {
		token = c.Query("castToken")
	}
	return token
}

// GetAccessTokenJWT fetches the JWT access token from the request header or query parameters.
// This token is issued by server on password verification of links that are protected by password.
func GetAccessTokenJWT(c *gin.Context) string {
	token := c.GetHeader("X-Auth-Access-Token-JWT")
	if token == "" {
		token = c.Query("accessTokenJWT")
	}
	return token
}

func MustGetPublicAccessContext(c *gin.Context) ente.PublicAccessContext {
	return c.MustGet(PublicAccessKey).(ente.PublicAccessContext)
}

func MustGetFileLinkAccessContext(c *gin.Context) *ente.FileLinkAccessContext {
	return c.MustGet(FileLinkAccessKey).(*ente.FileLinkAccessContext)
}

func GetCastCtx(c *gin.Context) cast.AuthContext {
	return c.MustGet(CastContext).(cast.AuthContext)
}
