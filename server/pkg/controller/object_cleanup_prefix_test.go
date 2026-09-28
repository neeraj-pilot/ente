package controller

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"sort"
	"strconv"
	"strings"
	"sync"
	"testing"

	"github.com/ente/museum/internal/testutil"
	"github.com/ente/museum/pkg/utils/config"
	"github.com/ente/museum/pkg/utils/s3config"
	"github.com/spf13/viper"
)

// pagedS3Mock emulates an S3-compatible ListObjectsV2 endpoint that returns
// at most pageSize keys per response and paginates with continuation tokens.
type pagedS3Mock struct {
	mu         sync.Mutex
	server     *httptest.Server
	objects    map[string]bool
	deleted    []string
	listCalls  int
	pageSize   int
	bucketName string
}

func (m *pagedS3Mock) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.URL.Query().Get("list-type") == "2" {
		m.serveList(w, r)
		return
	}
	key := strings.TrimPrefix(r.URL.Path, "/"+m.bucketName+"/")
	switch r.Method {
	case http.MethodDelete:
		m.mu.Lock()
		delete(m.objects, key)
		m.deleted = append(m.deleted, key)
		m.mu.Unlock()
		w.WriteHeader(http.StatusNoContent)
	case http.MethodHead:
		m.mu.Lock()
		_, ok := m.objects[key]
		m.mu.Unlock()
		if ok {
			w.WriteHeader(http.StatusOK)
		} else {
			w.WriteHeader(http.StatusNotFound)
		}
	default:
		w.WriteHeader(http.StatusMethodNotAllowed)
	}
}

func (m *pagedS3Mock) serveList(w http.ResponseWriter, r *http.Request) {
	prefix := r.URL.Query().Get("prefix")
	token := r.URL.Query().Get("continuation-token")

	m.mu.Lock()
	m.listCalls++
	var matched []string
	for key := range m.objects {
		if strings.HasPrefix(key, prefix) {
			matched = append(matched, key)
		}
	}
	sort.Strings(matched)
	start := 0
	if token != "" {
		var err error
		start, err = strconv.Atoi(token)
		if err != nil {
			m.mu.Unlock()
			w.WriteHeader(http.StatusBadRequest)
			return
		}
	}
	end := start + m.pageSize
	if end > len(matched) {
		end = len(matched)
	}
	page := matched[start:end]
	truncated := end < len(matched)
	nextToken := ""
	if truncated {
		nextToken = strconv.Itoa(end)
	}
	m.mu.Unlock()

	var sb strings.Builder
	sb.WriteString(`<?xml version="1.0" encoding="UTF-8"?>`)
	sb.WriteString(`<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">`)
	fmt.Fprintf(&sb, "<Name>%s</Name><Prefix>%s</Prefix>", m.bucketName, prefix)
	fmt.Fprintf(&sb, "<KeyCount>%d</KeyCount><MaxKeys>1000</MaxKeys>", len(page))
	if truncated {
		sb.WriteString("<IsTruncated>true</IsTruncated>")
		fmt.Fprintf(&sb, "<NextContinuationToken>%s</NextContinuationToken>", nextToken)
	} else {
		sb.WriteString("<IsTruncated>false</IsTruncated>")
	}
	for _, key := range page {
		fmt.Fprintf(&sb, "<Contents><Key>%s</Key><Size>1</Size><StorageClass>STANDARD</StorageClass></Contents>", key)
	}
	sb.WriteString("</ListBucketResult>")
	w.Header().Set("Content-Type", "application/xml")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(sb.String()))
}

func setupPrefixDeleteTest(t *testing.T, mock *pagedS3Mock) *ObjectCleanupController {
	t.Helper()
	testutil.WithServerRoot(t)
	viper.Reset()
	if err := config.ConfigureViper("local"); err != nil {
		t.Fatal(err)
	}
	viper.Set("s3.b2-eu-cen.key", "test-key")
	viper.Set("s3.b2-eu-cen.secret", "test-secret")
	viper.Set("s3.b2-eu-cen.endpoint", mock.serverURL())
	viper.Set("s3.b2-eu-cen.region", "us-east-1")
	viper.Set("s3.b2-eu-cen.bucket", mock.bucketName)
	viper.Set("s3.b2-eu-cen.disable_ssl", true)
	viper.Set("s3.use_path_style_urls", true)
	t.Cleanup(viper.Reset)

	return NewObjectCleanupController(nil, nil, s3config.NewS3Config())
}

func (m *pagedS3Mock) serverURL() string {
	return m.server.URL
}

func TestDeleteAllObjectsWithPrefixHandlesPagination(t *testing.T) {
	mock := &pagedS3Mock{
		objects: map[string]bool{
			"obj/1":  true,
			"obj/2":  true,
			"obj/3":  true,
			"keep/1": true,
		},
		pageSize:   2,
		bucketName: "test-bucket",
	}
	mock.server = httptest.NewServer(mock)
	t.Cleanup(mock.server.Close)

	controller := setupPrefixDeleteTest(t, mock)

	if err := controller.DeleteAllObjectsWithPrefix("obj/", "b2-eu-cen"); err != nil {
		t.Fatalf("DeleteAllObjectsWithPrefix returned error: %v", err)
	}

	mock.mu.Lock()
	defer mock.mu.Unlock()
	if len(mock.deleted) != 3 {
		t.Fatalf("deleted %d objects %v, want 3", len(mock.deleted), mock.deleted)
	}
	for _, key := range []string{"obj/1", "obj/2", "obj/3"} {
		if mock.objects[key] {
			t.Errorf("object %q was not deleted", key)
		}
	}
	if !mock.objects["keep/1"] {
		t.Errorf("unrelated object %q was deleted", "keep/1")
	}
	if mock.listCalls != 2 {
		t.Errorf("list was called %d times, want 2 (one truncated page plus the final page)", mock.listCalls)
	}
}
