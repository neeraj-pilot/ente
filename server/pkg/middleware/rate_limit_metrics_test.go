package middleware

import (
	"net/http"
	"testing"

	"github.com/prometheus/client_golang/prometheus/testutil"
)

func TestRecordRateLimitRejection(t *testing.T) {
	counter := rateLimitRejections.WithLabelValues(
		string(rateLimitScopeIP),
		http.MethodGet,
		"/test/rate-limit",
	)
	before := testutil.ToFloat64(counter)

	recordRateLimitRejection(rateLimitScopeIP, http.MethodGet, "/test/rate-limit")

	after := testutil.ToFloat64(counter)
	if after != before+1 {
		t.Fatalf("rate limit rejection counter = %v, want %v", after, before+1)
	}
}

func TestRateLimitScopeLabels(t *testing.T) {
	tests := []struct {
		scope rateLimitScope
		want  string
	}{
		{scope: rateLimitScopeIP, want: "ip"},
		{scope: rateLimitScopeUser, want: "user"},
		{scope: rateLimitScopeRouteGlobal, want: "route_global"},
		{scope: rateLimitScopeProcessGlobal, want: "process_global"},
	}

	for _, tt := range tests {
		if got := string(tt.scope); got != tt.want {
			t.Errorf("rate limit scope label = %q, want %q", got, tt.want)
		}
	}
}
