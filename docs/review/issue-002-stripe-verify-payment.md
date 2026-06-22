# Issue 002 — Stripe Payment Verification Fix

**Date:** 2026-06-22  
**Scope:** P0 — `StripeHttpPaymentProvider.verifyPayment()` always returned success  
**Status:** ✅ Implemented and tested

---

## Root Cause

`StripeHttpPaymentProvider.verifyPayment()` contained a hardcoded stub body:

```java
@Override
public PaymentVerificationResult verifyPayment(VerifyPaymentCommand command) {
    return new PaymentVerificationResult(true, "succeeded", "paid");
}
```

It made no HTTP call to Stripe. Every call unconditionally returned
`verified=true, externalState="succeeded", canonicalStatus="paid"`, regardless of the actual
Stripe payment state. This created a false-positive payment verification path — any caller
invoking this method would believe a payment had succeeded even if it had not.

The `HyperswitchHttpPaymentProvider` already had a correct implementation
(`GET /payments/{ref}` → parse `status` → fail closed on errors), which served as the
reference pattern for this fix.

---

## Investigation Findings

| Finding | Detail |
|---------|--------|
| `VerifyPaymentCommand.providerReference` | Stripe Checkout Session ID (e.g. `cs_live_...`) returned by `createCheckout` |
| Stripe API endpoint | `GET https://api.stripe.com/v1/checkout/sessions/{id}` |
| Success condition | `payment_status == "paid"` AND `status == "complete"` |
| HTTP client pattern | Raw `java.net.http.HttpClient` (no SDK), same as `createCheckout` |
| Local webhook state | `ProviderWebhookEventRepository` and `PaymentAttemptRepository` exist, but are not authoritative enough for verify — the API call is the correct choice |
| Auth header | `Authorization: Bearer {secretKey}` — same as `createCheckout` |

---

## Files Changed

| File | Change |
|------|--------|
| `payment-module/src/main/java/com/example/platform/payment/infrastructure/StripeHttpPaymentProvider.java` | Replaced stub `verifyPayment` with real Stripe API call; made `HttpClient` constructor-injectable |
| `payment-module/src/test/java/com/example/platform/payment/infrastructure/StripeHttpPaymentProviderTest.java` | New test class — 13 tests covering all verification paths |

---

## What Changed

### `verifyPayment` — before vs after

**Before (stub, always returns success):**
```java
@Override
public PaymentVerificationResult verifyPayment(VerifyPaymentCommand command) {
    return new PaymentVerificationResult(true, "succeeded", "paid");
}
```

**After (real Stripe API call):**
```java
@Override
public PaymentVerificationResult verifyPayment(VerifyPaymentCommand command) {
    String ref = command.providerReference();
    if (ref == null || ref.isBlank()) {
        return new PaymentVerificationResult(false, "missing_reference", "unknown");
    }
    try {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.stripe.com/v1/checkout/sessions/" + ref))
                .header("Authorization", "Bearer " + properties.getSecretKey())
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            return new PaymentVerificationResult(false, "http_" + response.statusCode(), "unknown");
        }
        String paymentStatus = extractJsonField(body, "payment_status");
        String sessionStatus  = extractJsonField(body, "status");
        boolean paid = "paid".equalsIgnoreCase(paymentStatus)
                    && "complete".equalsIgnoreCase(sessionStatus);
        return new PaymentVerificationResult(paid,
                paymentStatus != null ? paymentStatus : "unknown",
                paid ? "paid" : "pending");
    } catch (Exception e) {
        return new PaymentVerificationResult(false, "error", "unknown");
    }
}
```

### `HttpClient` constructor injection

`HttpClient` is now injected via a package-private constructor to enable unit testing without
live network calls. The public Spring-wired constructor still creates `HttpClient.newHttpClient()`
by default, so production behavior is unchanged.

```java
public StripeHttpPaymentProvider(StripePaymentProperties properties) {
    this(properties, HttpClient.newHttpClient());
}

/** Package-private — for testing only. */
StripeHttpPaymentProvider(StripePaymentProperties properties, HttpClient httpClient) { ... }
```

---

## Behavior Change

| Scenario | Before | After |
|----------|--------|-------|
| `payment_status=paid`, `status=complete` | ✅ verified (was stub) | ✅ verified (real API) |
| `payment_status=unpaid`, `status=open` | ✅ verified (false positive!) | ❌ not verified |
| `payment_status=unpaid`, `status=expired` | ✅ verified (false positive!) | ❌ not verified |
| `payment_status=no_payment_required` | ✅ verified (false positive!) | ❌ not verified |
| HTTP 404 (session not found) | ✅ verified (false positive!) | ❌ not verified, `externalState=http_404` |
| HTTP 401 (bad API key) | ✅ verified (false positive!) | ❌ not verified, `externalState=http_401` |
| Network exception | ✅ verified (false positive!) | ❌ not verified, `externalState=error` |
| Blank/null providerReference | ✅ verified (false positive!) | ❌ not verified, `externalState=missing_reference` |
| Noop provider (dev/test) | unchanged — still returns true | unchanged |

---

## Tests Added

**`StripeHttpPaymentProviderTest`** — 13 new tests, 0 failures:

| Test | Path verified |
|------|--------------|
| `verifyPaymentReturnsFalseForNullReference` | Null ref → missing_reference |
| `verifyPaymentReturnsFalseForBlankReference` | Blank ref → missing_reference |
| `verifyPaymentReturnsTrueWhenStatusCompleteAndPaymentStatusPaid` | Happy path — success |
| `verifyPaymentReturnsFalseWhenPaymentStatusUnpaid` | unpaid → false |
| `verifyPaymentReturnsFalseWhenSessionExpired` | expired → false |
| `verifyPaymentReturnsFalseForNoPaymentRequiredStatus` | no_payment_required → false |
| `verifyPaymentReturnsFalseOnHttp404` | HTTP 404 → false |
| `verifyPaymentReturnsFalseOnHttp401` | HTTP 401 → false |
| `verifyPaymentReturnsFalseOnHttp500` | HTTP 500 → false |
| `verifyPaymentReturnsFalseOnNetworkException` | IOException → false, error |
| `verifyPaymentReturnsFalseOnMalformedResponseBody` | Non-JSON → false |
| `verifyPaymentReturnsFalseWhenPaymentStatusMissingFromResponse` | Missing field → false |
| `codeReturnsStripe` | Provider code = "stripe" |

---

## Commands Run

```bash
# New StripeHttpPaymentProvider tests
./gradlew :payment-module:test \
  --tests "com.example.platform.payment.infrastructure.StripeHttpPaymentProviderTest" \
  --no-daemon

# Full payment-module test suite (regression check)
./gradlew :payment-module:test --no-daemon
```

---

## Test Results

| Suite | Tests | Failures | Errors |
|-------|-------|----------|--------|
| `StripeHttpPaymentProviderTest` | 13 | 0 | 0 |
| `HyperswitchHttpPaymentProviderTest` | 3 | 0 | 0 |
| `PaymentGatewayServiceTest` | 15 | 0 | 0 |
| `StripeWebhookSignatureVerifierTest` | 2 | 0 | 0 |
| **Total** | **33** | **0** | **0** |

---

## Remaining Risks

1. **`HyperswitchHttpPaymentProvider.verifyPayment`** follows the same correct pattern but
   was not changed. Its `verifyPayment` has already been confirmed correct in the investigation.

2. **`verifyPayment` is a polling/secondary path.** The authoritative confirmation path is the
   webhook flow (`parseWebhook` → `PaymentSucceededCheckoutHandler` → `CheckoutOrchestrator`).
   `verifyPayment` is used as a fallback or reconciliation check. Callers should prefer
   webhook-confirmed state when available.

3. **Secret key not logged.** The `Authorization` header uses `properties.getSecretKey()`.
   The implementation only logs the `ref`, `paymentStatus`, `sessionStatus`, and `verified`
   fields — no secret values are emitted in log output.

4. **No Stripe signature verification on the verify path.** `verifyPayment` uses the secret
   key to authenticate outbound requests (Bearer token), which is correct for REST API calls.
   Webhook signature verification is a separate concern handled by
   `StripeWebhookSignatureVerifier` and is not affected by this change.

---

## Recommended Next Steps

1. Audit all callers of `PaymentGatewayService.verifyPayment()` to confirm they now correctly
   handle `verified=false` results — the previous stub meant callers may have been written
   assuming this always returns true.
2. Consider a `StripeWebhookSignatureVerifier` guard on `parseWebhook` to enforce
   `Stripe-Signature` header validation when `platform.payment.webhook.allow-unsigned=false`.
3. Add a note in `.env.example` that `PLATFORM_PAYMENT_STRIPE_SECRET_KEY` must be a live
   secret key, not a test placeholder, in production.
