package com.example.platform.payment.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.payment.domain.PaymentVerificationResult;
import com.example.platform.payment.domain.VerifyPaymentCommand;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class StripeHttpPaymentProviderTest {

    // -------------------------------------------------------------------------
    // Stub infrastructure — avoids live network calls in unit tests
    // -------------------------------------------------------------------------

    /**
     * Minimal {@link HttpClient} skeleton. Subclass only needs to override {@link #send}.
     * All async/config methods throw UnsupportedOperationException or return empty defaults.
     */
    private abstract static class StubHttpClientBase extends HttpClient {
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NORMAL; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() {
            try { return SSLContext.getDefault(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest req, BodyHandler<T> h) {
            throw new UnsupportedOperationException("stub");
        }
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest req, BodyHandler<T> h, PushPromiseHandler<T> p) {
            throw new UnsupportedOperationException("stub");
        }
    }

    private static final class StubHttpResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final T body;
        private final URI uri;

        StubHttpResponse(int statusCode, T body, URI uri) {
            this.statusCode = statusCode;
            this.body = body;
            this.uri = uri;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public T body() { return body; }
        @Override public URI uri() { return uri; }
        @Override public HttpRequest request() { throw new UnsupportedOperationException("stub"); }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    /** Returns a stub HttpClient that always replies with the given status and body string. */
    private static HttpClient stubClient(int status, String responseBody) {
        return new StubHttpClientBase() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler)
                    throws IOException, InterruptedException {
                return (HttpResponse<T>) new StubHttpResponse<>(status, responseBody, request.uri());
            }
        };
    }

    /** Returns a stub HttpClient that always throws IOException on send. */
    private static HttpClient failingClient(String message) {
        return new StubHttpClientBase() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler)
                    throws IOException {
                throw new IOException(message);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static StripePaymentProperties propsWithKey(String secretKey) {
        StripePaymentProperties props = new StripePaymentProperties();
        props.setEnabled(true);
        props.setSecretKey(secretKey);
        return props;
    }

    private static final String PAID_SESSION_JSON = """
            {"id":"cs_live_abc","object":"checkout.session",\
            "status":"complete","payment_status":"paid",\
            "client_reference_id":"chk_1"}""";

    private static final String UNPAID_SESSION_JSON = """
            {"id":"cs_live_abc","object":"checkout.session",\
            "status":"open","payment_status":"unpaid",\
            "client_reference_id":"chk_1"}""";

    private static final String EXPIRED_SESSION_JSON = """
            {"id":"cs_live_abc","object":"checkout.session",\
            "status":"expired","payment_status":"unpaid",\
            "client_reference_id":"chk_1"}""";

    private static final String NO_PAYMENT_REQUIRED_JSON = """
            {"id":"cs_live_abc","object":"checkout.session",\
            "status":"complete","payment_status":"no_payment_required"}""";

    // -------------------------------------------------------------------------
    // verifyPayment — early-return guards
    // -------------------------------------------------------------------------

    @Test
    void verifyPaymentReturnsFalseForNullReference() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), stubClient(200, PAID_SESSION_JSON));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand(null, "{}"));

        assertFalse(result.verified());
        assertEquals("missing_reference", result.externalState());
        assertEquals("unknown", result.canonicalStatus());
    }

    @Test
    void verifyPaymentReturnsFalseForBlankReference() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), stubClient(200, PAID_SESSION_JSON));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("   ", "{}"));

        assertFalse(result.verified());
        assertEquals("missing_reference", result.externalState());
    }

    // -------------------------------------------------------------------------
    // verifyPayment — successful paid session
    // -------------------------------------------------------------------------

    @Test
    void verifyPaymentReturnsTrueWhenStatusCompleteAndPaymentStatusPaid() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), stubClient(200, PAID_SESSION_JSON));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("cs_live_abc", "{}"));

        assertTrue(result.verified());
        assertEquals("paid", result.externalState());
        assertEquals("paid", result.canonicalStatus());
    }

    // -------------------------------------------------------------------------
    // verifyPayment — failure / unpaid / partial states all fail closed
    // -------------------------------------------------------------------------

    @Test
    void verifyPaymentReturnsFalseWhenPaymentStatusUnpaid() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), stubClient(200, UNPAID_SESSION_JSON));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("cs_live_abc", "{}"));

        assertFalse(result.verified());
        assertEquals("unpaid", result.externalState());
        assertEquals("pending", result.canonicalStatus());
    }

    @Test
    void verifyPaymentReturnsFalseWhenSessionExpired() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), stubClient(200, EXPIRED_SESSION_JSON));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("cs_live_abc", "{}"));

        assertFalse(result.verified());
        assertEquals("pending", result.canonicalStatus());
    }

    @Test
    void verifyPaymentReturnsFalseForNoPaymentRequiredStatus() {
        // "no_payment_required" is not a valid paid state — fail closed
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), stubClient(200, NO_PAYMENT_REQUIRED_JSON));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("cs_live_abc", "{}"));

        assertFalse(result.verified());
    }

    // -------------------------------------------------------------------------
    // verifyPayment — HTTP error codes
    // -------------------------------------------------------------------------

    @Test
    void verifyPaymentReturnsFalseOnHttp404() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"),
                stubClient(404, "{\"error\":{\"message\":\"No such checkout.session\"}}"));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("cs_live_notfound", "{}"));

        assertFalse(result.verified());
        assertEquals("http_404", result.externalState());
        assertEquals("unknown", result.canonicalStatus());
    }

    @Test
    void verifyPaymentReturnsFalseOnHttp401() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"),
                stubClient(401, "{\"error\":{\"message\":\"Invalid API key\"}}"));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("cs_live_abc", "{}"));

        assertFalse(result.verified());
        assertEquals("http_401", result.externalState());
    }

    @Test
    void verifyPaymentReturnsFalseOnHttp500() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), stubClient(500, "{\"error\":{\"message\":\"Server error\"}}"));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("cs_live_abc", "{}"));

        assertFalse(result.verified());
        assertEquals("http_500", result.externalState());
    }

    // -------------------------------------------------------------------------
    // verifyPayment — network and parse failures
    // -------------------------------------------------------------------------

    @Test
    void verifyPaymentReturnsFalseOnNetworkException() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), failingClient("Connection refused"));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("cs_live_abc", "{}"));

        assertFalse(result.verified());
        assertEquals("error", result.externalState());
        assertEquals("unknown", result.canonicalStatus());
    }

    @Test
    void verifyPaymentReturnsFalseOnMalformedResponseBody() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), stubClient(200, "not-json-at-all"));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("cs_live_abc", "{}"));

        // payment_status will be null → not "paid" → verified=false, canonicalStatus="pending"
        assertFalse(result.verified());
        assertEquals("pending", result.canonicalStatus());
    }

    @Test
    void verifyPaymentReturnsFalseWhenPaymentStatusMissingFromResponse() {
        // status=complete but payment_status field absent — should not be treated as paid
        String incompleteJson = """
                {"id":"cs_live_abc","object":"checkout.session","status":"complete"}""";
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), stubClient(200, incompleteJson));

        PaymentVerificationResult result = provider.verifyPayment(
                new VerifyPaymentCommand("cs_live_abc", "{}"));

        assertFalse(result.verified());
    }

    // -------------------------------------------------------------------------
    // provider code
    // -------------------------------------------------------------------------

    @Test
    void codeReturnsStripe() {
        var provider = new StripeHttpPaymentProvider(
                propsWithKey("sk_test"), stubClient(200, "{}"));
        assertEquals("stripe", provider.code().value());
    }
}
