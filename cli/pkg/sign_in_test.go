package pkg

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"testing"

	"github.com/ente/cli/internal/api"
	"golang.org/x/crypto/nacl/box"
	"golang.org/x/crypto/nacl/secretbox"
)

func TestDecryptAccountToken(t *testing.T) {
	publicKey, secretKey, err := box.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	kek, masterKey, nonce := [32]byte{1}, [32]byte{2}, [24]byte{3}
	attributes := &api.KeyAttributes{
		PublicKey:                base64.StdEncoding.EncodeToString(publicKey[:]),
		EncryptedKey:             base64.StdEncoding.EncodeToString(secretbox.Seal(nil, masterKey[:], &nonce, &kek)),
		KeyDecryptionNonce:       base64.StdEncoding.EncodeToString(nonce[:]),
		EncryptedSecretKey:       base64.StdEncoding.EncodeToString(secretbox.Seal(nil, secretKey[:], &nonce, &masterKey)),
		SecretKeyDecryptionNonce: base64.StdEncoding.EncodeToString(nonce[:]),
	}
	token := append([]byte("auth-token"), bytes.Repeat([]byte{255}, 32)...)
	for _, tc := range []struct {
		name    string
		payload []byte
		valid   bool
	}{
		{"tagged token", token, true},
		{"unrelated secret", bytes.Repeat([]byte{7}, 32), false},
		{"deletion challenge", []byte("delete-jwtheader.payload.signature"), false},
		{"wrong tag", append([]byte("wrong-type"), token[10:]...), false},
		{"short token", token[:41], false},
		{"long token", append(append([]byte(nil), token...), 0), false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			ciphertext, err := box.SealAnonymous(nil, tc.payload, publicKey, rand.Reader)
			if err != nil {
				t.Fatal(err)
			}
			response := &api.AuthorizationResponse{
				KeyAttributes:  attributes,
				EncryptedToken: base64.StdEncoding.EncodeToString(ciphertext),
			}
			account, err := (&ClICtrl{}).decryptAccSecretInfo(context.Background(), response, kek[:])
			if !tc.valid {
				if err == nil || account != nil {
					t.Fatal("substituted token was returned")
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.Equal(account.Token, token) || !bytes.Equal(account.MasterKey, masterKey[:]) {
				t.Fatal("account secrets changed")
			}
		})
	}
}
