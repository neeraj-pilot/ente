package controller

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/ente/museum/ente"
	"github.com/ente/museum/internal/testutil"
	"github.com/ente/museum/pkg/repo"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/stripe/stripe-go/v72"
	"github.com/stripe/stripe-go/v72/client"
)

func TestStripeSubscriptionInvoiceValidation(t *testing.T) {
	for _, tc := range []struct {
		name           string
		invoiceStatus  stripe.InvoiceStatus
		paymentStatus  stripe.PaymentIntentStatus
		missingInvoice bool
		allowed        bool
	}{
		{name: "paid without payment intent", invoiceStatus: stripe.InvoiceStatusPaid, allowed: true},
		{name: "delayed payment processing", invoiceStatus: stripe.InvoiceStatusOpen, paymentStatus: stripe.PaymentIntentStatusProcessing, allowed: true},
		{name: "failed payment", invoiceStatus: stripe.InvoiceStatusOpen, paymentStatus: stripe.PaymentIntentStatusRequiresPaymentMethod},
		{name: "authentication required", invoiceStatus: stripe.InvoiceStatusOpen, paymentStatus: stripe.PaymentIntentStatusRequiresAction},
		{name: "void delayed payment", invoiceStatus: stripe.InvoiceStatusVoid, paymentStatus: stripe.PaymentIntentStatusProcessing},
		{name: "uncollectible", invoiceStatus: stripe.InvoiceStatusUncollectible},
		{name: "draft", invoiceStatus: stripe.InvoiceStatusDraft},
		{name: "open without payment intent", invoiceStatus: stripe.InvoiceStatusOpen},
		{name: "missing invoice", missingInvoice: true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			subscription, invoice := stripeTestSubscription()
			invoice.Status = tc.invoiceStatus
			if tc.paymentStatus != "" {
				invoice.PaymentIntent = &stripe.PaymentIntent{Status: tc.paymentStatus}
			}
			if tc.missingInvoice {
				subscription.LatestInvoice = nil
			}
			controller := stripeTestController(t, func(w http.ResponseWriter, r *http.Request) {
				assert.Equal(t, "/v1/invoices/in_current", r.URL.Path)
				assert.Equal(t, "payment_intent", r.URL.Query().Get("expand[0]"))
				assert.NoError(t, json.NewEncoder(w).Encode(invoice))
			})

			verified, err := controller.getEnteSubscriptionFromStripeSubscription(1, subscription)
			if tc.allowed {
				require.NoError(t, err)
				require.Equal(t, subscription.CurrentPeriodEnd*1_000_000, verified.ExpiryTime)
				require.EqualValues(t, 100, verified.Storage)
			} else {
				require.ErrorIs(t, err, ente.ErrNoActiveSubscription)
			}
		})
	}
}

func TestStripeVerificationAfterPaymentFailure(t *testing.T) {
	for _, sessionID := range []string{"", "cs_checkout"} {
		for _, status := range []stripe.InvoiceStatus{stripe.InvoiceStatusVoid, stripe.InvoiceStatusPaid, stripe.InvoiceStatusOpen} {
			t.Run(sessionID+"/"+string(status), func(t *testing.T) {
				subscription, invoice := stripeTestSubscription()
				invoice.Status = status
				if status == stripe.InvoiceStatusOpen {
					invoice.PaymentIntent = &stripe.PaymentIntent{Status: stripe.PaymentIntentStatusProcessing}
				}
				controller := stripeTestController(t, func(w http.ResponseWriter, r *http.Request) {
					switch r.URL.Path {
					case "/v1/subscriptions/sub_current":
						assert.NoError(t, json.NewEncoder(w).Encode(subscription))
					case "/v1/checkout/sessions/cs_checkout":
						assert.NoError(t, json.NewEncoder(w).Encode(stripe.CheckoutSession{
							ClientReferenceID: "1", Subscription: &subscription,
						}))
					case "/v1/invoices/in_current":
						assert.NoError(t, json.NewEncoder(w).Encode(invoice))
					default:
						t.Errorf("unexpected Stripe request: %s", r.URL.Path)
						w.WriteHeader(http.StatusNotFound)
					}
				})
				current := stripeTestStoredSubscription(t, controller)
				billing := &BillingController{BillingRepo: controller.BillingRepo, StripeController: controller}

				_, err := billing.VerifySubscription(1, ente.Stripe, "", sessionID)
				stored, readErr := controller.BillingRepo.GetUserSubscription(1)
				require.NoError(t, readErr)
				if status == stripe.InvoiceStatusVoid {
					require.ErrorIs(t, err, ente.ErrNoActiveSubscription)
					require.Equal(t, current, stored)
				} else {
					require.NoError(t, err)
					require.Equal(t, subscription.CurrentPeriodEnd*1_000_000, stored.ExpiryTime)
				}
			})
		}
	}
}

func TestStripeInvoicePaidDoesNotGrantLaterUnpaidPeriod(t *testing.T) {
	for _, tc := range []struct {
		name            string
		invoiceID       string
		paidDays        int
		productID       string
		proration       bool
		pendingUpgrade  bool
		paginated       bool
		paginationError bool
	}{
		{name: "old paid period", invoiceID: "in_previous", paidDays: -1, productID: "price_current"},
		{name: "current paid period", invoiceID: "in_current", paidDays: 30, productID: "price_current"},
		{name: "paid renewal before pending upgrade", invoiceID: "in_previous", paidDays: 30, productID: "price_current", pendingUpgrade: true},
		{name: "different plan", invoiceID: "in_current", paidDays: 30, productID: "price_previous"},
		{name: "paid upgrade proration", invoiceID: "in_current", paidDays: 30, productID: "price_current", proration: true},
		{name: "paid renewal on later page", invoiceID: "in_current", paidDays: 30, productID: "price_current", paginated: true},
		{name: "later invoice page unavailable", invoiceID: "in_current", paidDays: 30, productID: "price_current", paginated: true, paginationError: true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			subscription, _ := stripeTestSubscription()
			if tc.pendingUpgrade {
				subscription.Status = stripe.SubscriptionStatusPastDue
				subscription.Items.Data[0].Price.ID = "price_upgrade"
			}
			paidThrough := time.Now().AddDate(0, 0, tc.paidDays).Unix()
			lineType := stripe.InvoiceLineTypeSubscription
			if tc.proration {
				lineType = stripe.InvoiceLineTypeInvoiceItem
			}
			paidLine := &stripe.InvoiceLine{
				ID: "il_subscription", Type: lineType, Proration: tc.proration, Subscription: subscription.ID,
				Price: &stripe.Price{ID: tc.productID}, Amount: 100,
				Period: &stripe.Period{End: paidThrough},
			}
			firstPage := &stripe.InvoiceLineList{Data: []*stripe.InvoiceLine{paidLine}}
			if tc.paginated {
				firstPage = &stripe.InvoiceLineList{
					ListMeta: stripe.ListMeta{HasMore: true},
					Data:     []*stripe.InvoiceLine{{ID: "il_unrelated", Type: stripe.InvoiceLineTypeInvoiceItem, Amount: 100}},
				}
			}
			controller := stripeTestController(t, func(w http.ResponseWriter, r *http.Request) {
				switch r.URL.Path {
				case "/v1/subscriptions/sub_current":
					assert.NoError(t, json.NewEncoder(w).Encode(subscription))
				case "/v1/invoices/" + tc.invoiceID + "/lines":
					assert.True(t, tc.paginated)
					if r.URL.Query().Get("starting_after") == "" {
						assert.NoError(t, json.NewEncoder(w).Encode(firstPage))
					} else {
						assert.Equal(t, "il_unrelated", r.URL.Query().Get("starting_after"))
						if tc.paginationError {
							http.Error(w, `{"error":{"type":"api_error","message":"invoice lines unavailable"}}`, http.StatusInternalServerError)
							return
						}
						assert.NoError(t, json.NewEncoder(w).Encode(stripe.InvoiceLineList{Data: []*stripe.InvoiceLine{paidLine}}))
					}
				default:
					t.Errorf("unexpected Stripe request: %s", r.URL.Path)
					w.WriteHeader(http.StatusNotFound)
				}
			})
			current := stripeTestStoredSubscription(t, controller)
			payload, err := json.Marshal(stripe.Invoice{
				ID: tc.invoiceID, Status: stripe.InvoiceStatusPaid, Subscription: &stripe.Subscription{ID: subscription.ID},
				Lines: firstPage,
			})
			require.NoError(t, err)
			_, err = controller.handleInvoicePaid(stripe.Event{Data: &stripe.EventData{Raw: payload}}, ente.StripeUS)
			if tc.paginationError {
				require.ErrorContains(t, err, "fetch lines for paid Stripe invoice "+tc.invoiceID)
				stored, readErr := controller.BillingRepo.GetUserSubscription(1)
				require.NoError(t, readErr)
				require.Equal(t, current, stored)
				return
			}
			require.NoError(t, err)
			stored, err := controller.BillingRepo.GetUserSubscription(1)
			require.NoError(t, err)
			if tc.paidDays < 0 || tc.productID != current.ProductID {
				require.Equal(t, current, stored)
			} else {
				require.Equal(t, paidThrough*1_000_000, stored.ExpiryTime)
				require.Equal(t, current.ProductID, stored.ProductID)
			}
		})
	}
}

func TestStripeProductUpdateUsesCurrentInvoice(t *testing.T) {
	for _, status := range []stripe.InvoiceStatus{stripe.InvoiceStatusVoid, stripe.InvoiceStatusDraft, stripe.InvoiceStatusPaid, stripe.InvoiceStatusOpen} {
		t.Run(string(status), func(t *testing.T) {
			subscription, invoice := stripeTestSubscription()
			invoice.Status = status
			if status == stripe.InvoiceStatusOpen {
				invoice.PaymentIntent = &stripe.PaymentIntent{Status: stripe.PaymentIntentStatusProcessing}
			}
			controller := stripeTestController(t, func(w http.ResponseWriter, r *http.Request) {
				switch r.URL.Path {
				case "/v1/subscriptions/sub_current":
					assert.NoError(t, json.NewEncoder(w).Encode(subscription))
				case "/v1/invoices/in_current":
					assert.NoError(t, json.NewEncoder(w).Encode(invoice))
				default:
					t.Errorf("unexpected Stripe request: %s", r.URL.Path)
					w.WriteHeader(http.StatusNotFound)
				}
			})
			current := stripeTestStoredSubscription(t, controller)
			current.ProductID = "price_previous"
			require.NoError(t, controller.BillingRepo.ReplaceSubscription(current.ID, current))
			eventSubscription := subscription
			eventSubscription.LatestInvoice = &stripe.Invoice{ID: "in_previous", Status: stripe.InvoiceStatusPaid}
			payload, err := json.Marshal(eventSubscription)
			require.NoError(t, err)
			_, err = controller.handleCustomerSubscriptionUpdated(stripe.Event{Data: &stripe.EventData{Raw: payload}}, ente.StripeUS)
			require.NoError(t, err)
			stored, err := controller.BillingRepo.GetUserSubscription(1)
			require.NoError(t, err)
			if status == stripe.InvoiceStatusVoid || status == stripe.InvoiceStatusDraft {
				require.Equal(t, current, stored)
				oldInvoice := invoice
				oldInvoice.ID = "in_previous"
				oldInvoice.Status = stripe.InvoiceStatusPaid
				payload, err = json.Marshal(oldInvoice)
				require.NoError(t, err)
				_, err = controller.handleInvoicePaid(stripe.Event{Data: &stripe.EventData{Raw: payload}}, ente.StripeUS)
				require.NoError(t, err)
				stored, err = controller.BillingRepo.GetUserSubscription(1)
				require.NoError(t, err)
				require.Equal(t, current, stored)
			} else {
				require.Equal(t, "price_current", stored.ProductID)
				require.Equal(t, subscription.CurrentPeriodEnd*1_000_000, stored.ExpiryTime)
			}
			if status == stripe.InvoiceStatusDraft {
				invoice.Status = stripe.InvoiceStatusPaid
				payload, err = json.Marshal(invoice)
				require.NoError(t, err)
				_, err = controller.handleInvoicePaid(stripe.Event{Data: &stripe.EventData{Raw: payload}}, ente.StripeUS)
				require.NoError(t, err)
				stored, err = controller.BillingRepo.GetUserSubscription(1)
				require.NoError(t, err)
				require.Equal(t, "price_current", stored.ProductID)
				require.Equal(t, subscription.CurrentPeriodEnd*1_000_000, stored.ExpiryTime)
			}
		})
	}
}

func stripeTestSubscription() (stripe.Subscription, stripe.Invoice) {
	subscription := stripe.Subscription{
		ID: "sub_current", Status: stripe.SubscriptionStatusActive,
		Customer:         &stripe.Customer{ID: "cus_current"},
		CurrentPeriodEnd: time.Now().AddDate(1, 0, 0).Unix(),
		Items: &stripe.SubscriptionItemList{Data: []*stripe.SubscriptionItem{
			{Price: &stripe.Price{ID: "price_current"}},
		}},
		LatestInvoice: &stripe.Invoice{ID: "in_current"},
	}
	return subscription, stripe.Invoice{ID: "in_current", Subscription: &stripe.Subscription{ID: subscription.ID}}
}

func stripeTestController(t *testing.T, handler http.HandlerFunc) *StripeController {
	t.Helper()
	server := httptest.NewServer(handler)
	t.Cleanup(server.Close)
	backend := stripe.GetBackendWithConfig(stripe.APIBackend, &stripe.BackendConfig{
		URL: stripe.String(server.URL), HTTPClient: server.Client(), MaxNetworkRetries: stripe.Int64(0),
	})
	stripeClient := &client.API{}
	stripeClient.Init("sk_test", &stripe.Backends{API: backend, Connect: backend, Uploads: backend})
	return &StripeController{
		StripeClients: ente.StripeClientPerAccount{ente.StripeUS: stripeClient},
		BillingPlansPerAccount: ente.BillingPlansPerAccount{
			ente.StripeUS: {"EU": {{StripeID: "price_current", Storage: 100}}},
		},
	}
}

func stripeTestStoredSubscription(t *testing.T, controller *StripeController) ente.Subscription {
	t.Helper()
	db := testutil.RequireTestDB(t)
	testutil.ResetTables(t, db)
	testutil.InsertUser(t, db, testutil.UserFixture{UserID: 1, Email: "stripe-test@ente.com", CreationTime: 1})
	testutil.InsertSubscription(t, db, testutil.SubscriptionFixture{
		UserID: 1, Storage: 100, ProductID: "price_current", PaymentProvider: ente.Stripe,
		OriginalTransactionID: "sub_current", ExpiryTime: time.Now().UnixMicro(),
		Attributes: `{"stripeAccountCountry":"US","customerID":"cus_current","isCancelled":true}`,
	})
	controller.BillingRepo = &repo.BillingRepository{DB: db}
	subscription, err := controller.BillingRepo.GetUserSubscription(1)
	require.NoError(t, err)
	return subscription
}
