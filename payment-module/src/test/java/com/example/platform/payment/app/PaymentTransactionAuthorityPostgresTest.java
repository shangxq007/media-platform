package com.example.platform.payment.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.payment.domain.ApplyWebhookCommand;
import com.example.platform.payment.domain.CheckoutResult;
import com.example.platform.payment.domain.InitiateCheckoutCommand;
import com.example.platform.payment.domain.PaymentProvider;
import com.example.platform.payment.domain.PaymentState;
import com.example.platform.payment.domain.PaymentTransaction;
import com.example.platform.payment.domain.PaymentVerificationResult;
import com.example.platform.payment.domain.ProviderCode;
import com.example.platform.payment.domain.ProviderRefundRequest;
import com.example.platform.payment.domain.ProviderRefundResult;
import com.example.platform.payment.domain.ProviderVerificationRequest;
import com.example.platform.payment.domain.RefundPaymentCommand;
import com.example.platform.payment.domain.VerifyPaymentCommand;
import com.example.platform.payment.domain.WebhookParseResult;
import com.example.platform.payment.infrastructure.PaymentTransactionJdbcRepository;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class PaymentTransactionAuthorityPostgresTest extends PostgresTestContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");
    private static DataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static PaymentTransactionAuthority authority;
    private static RecordingProvider provider;

    @BeforeAll
    static void createSchema() {
        dataSource = createDataSource();
        TestConfiguration.dataSource = dataSource;
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        authority = context.getBean(PaymentTransactionAuthority.class);
        provider = context.getBean(RecordingProvider.class);
        jdbc.execute("DROP TABLE IF EXISTS payment_refund, payment_outbox, provider_webhook_receipt, payment_command, payment_transaction CASCADE");
        jdbc.execute("""
                CREATE TABLE payment_transaction (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    principal_type varchar(32) not null, principal_id varchar(128) not null,
                    workspace_id varchar(64) not null default '', organization_id varchar(64) not null default '',
                    order_id varchar(64), checkout_session_id varchar(64) not null,
                    provider_code varchar(64) not null, provider_reference varchar(255), redirect_url text,
                    amount_minor bigint not null, currency_code varchar(3) not null,
                    transaction_state varchar(32) not null, provider_event_cursor bigint,
                    captured_amount_minor bigint not null default 0,
                    refunded_amount_minor bigint not null default 0,
                    version bigint not null, provider_call_claimed_at timestamptz,
                    source varchar(128) not null, trace_id varchar(128) not null,
                    created_at timestamptz not null, updated_at timestamptz not null,
                    unique (tenant_id, checkout_session_id), unique (provider_code, provider_reference),
                    check (captured_amount_minor >= 0), check (refunded_amount_minor >= 0),
                    check (refunded_amount_minor <= captured_amount_minor)
                )
                """);
        jdbc.execute("""
                CREATE TABLE payment_command (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    principal_type varchar(32) not null, principal_id varchar(128) not null,
                    workspace_id varchar(64) not null default '', organization_id varchar(64) not null default '',
                    idempotency_key varchar(255) not null, command_type varchar(32) not null,
                    transaction_id varchar(64) not null, payload_fingerprint varchar(64) not null,
                    result_fingerprint varchar(64), result_state varchar(32) not null,
                    result_version bigint not null, source varchar(128) not null,
                    reason varchar(512) not null, trace_id varchar(128) not null,
                    created_at timestamptz not null, unique (tenant_id, idempotency_key)
                )
                """);
        jdbc.execute("""
                CREATE TABLE provider_webhook_receipt (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    provider_code varchar(64) not null,
                    event_id varchar(255) not null, payload_sha256 varchar(64) not null,
                    event_type varchar(128) not null, event_cursor bigint not null,
                    provider_reference varchar(255) not null, canonical_state varchar(32) not null,
                    processing_outcome varchar(32) not null, transaction_id varchar(64) not null,
                    occurred_at timestamptz not null, received_at timestamptz not null,
                    unique (provider_code, event_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE payment_outbox (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    event_type varchar(64) not null, aggregate_id varchar(64) not null,
                    dedupe_key varchar(255) not null, provider_code varchar(64) not null,
                    provider_reference varchar(255) not null, checkout_session_id varchar(64) not null,
                    trace_id varchar(128) not null, created_at timestamptz not null,
                    dispatched_at timestamptz, unique (tenant_id, event_type, dedupe_key)
                )
                """);
        jdbc.execute("""
                CREATE TABLE payment_refund (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    transaction_id varchar(64) not null, provider_refund_reference varchar(255),
                    original_capture_reference varchar(255) not null,
                    amount_minor bigint not null, currency_code varchar(3) not null,
                    refund_state varchar(32) not null, provider_call_claimed_at timestamptz,
                    idempotency_key varchar(255) not null,
                    payload_fingerprint varchar(64) not null, source varchar(128) not null,
                    reason varchar(512) not null, trace_id varchar(128) not null,
                    created_at timestamptz not null, updated_at timestamptz not null,
                    unique (tenant_id, idempotency_key), check (amount_minor > 0)
                )
                """);
    }

    @AfterAll
    static void closeDatabase() {
        if (context != null) context.close();
        closeDataSource(dataSource);
    }

    @BeforeEach
    void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_payment_intent ON payment_transaction");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_payment_outbox ON payment_outbox");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_payment_refund_projection ON payment_transaction");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_payment_test()");
        jdbc.execute("TRUNCATE payment_refund, payment_outbox, provider_webhook_receipt, payment_command, payment_transaction");
        provider.reset();
    }

    @Test
    void checkoutIntentPrecedesProviderCallAndReplayRejectsPayloadMismatch() {
        InitiateCheckoutCommand command = checkout("tenant-a", "txn-1", "checkout-1", "checkout-key", 1000);
        PaymentTransaction first = authority.initiateCheckout(command);
        PaymentTransaction replay = authority.initiateCheckout(command);

        assertEquals(first, replay);
        assertEquals(1, provider.checkoutCalls.get());
        assertEquals(PaymentState.PENDING, first.state());
        assertThrows(IllegalStateException.class, () -> authority.initiateCheckout(
                checkout("tenant-a", "txn-other", "checkout-2", "checkout-key", 999)));

        installFailureTrigger("fail_payment_intent", "payment_transaction", "BEFORE", "INSERT");
        assertThrows(RuntimeException.class, () -> authority.initiateCheckout(
                checkout("tenant-a", "txn-fail", "checkout-fail", "fail-key", 500)));
        assertEquals(1, provider.checkoutCalls.get(), "provider must not run before durable intent");
    }

    @Test
    void durableCheckoutReplayPrecedesCurrentProviderAvailability() {
        InitiateCheckoutCommand command = checkout(
                "tenant-a", "txn-durable-replay", "checkout-durable-replay",
                "durable-replay-key", 1000);
        PaymentTransaction first = authority.initiateCheckout(command);
        PaymentTransactionAuthority withoutProviders = new PaymentTransactionAuthority(
                java.util.List.of(), new PaymentTransactionJdbcRepository(jdbc),
                context.getBean(PlatformTransactionManager.class));

        PaymentTransaction replay = withoutProviders.initiateCheckout(command);

        assertEquals(first, replay);
        assertEquals(1, provider.checkoutCalls.get(), "exact replay must not execute again");
        assertThrows(IllegalStateException.class, () -> withoutProviders.initiateCheckout(
                checkout("tenant-a", "txn-different", "checkout-different",
                        "durable-replay-key", 999)));
        assertEquals(1, provider.checkoutCalls.get(), "conflicting replay must not execute");
    }

    @Test
    void concurrentCheckoutReplayClaimsOneProviderSideEffect() throws Exception {
        InitiateCheckoutCommand command = checkout(
                "tenant-a", "txn-checkout-duplicate", "checkout-duplicate", "checkout-duplicate-key", 1000);
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var one = pool.submit(() -> executeCheckout(start, command));
        var two = pool.submit(() -> executeCheckout(start, command));
        start.countDown();
        int successes = (one.get(10, TimeUnit.SECONDS) ? 1 : 0) + (two.get(10, TimeUnit.SECONDS) ? 1 : 0);
        pool.shutdownNow();
        org.junit.jupiter.api.Assertions.assertTrue(successes >= 1);
        assertEquals(1, provider.checkoutCalls.get());
        assertEquals(1, count("payment_transaction"));
        assertEquals(PaymentState.PENDING,
                authority.find(principal("tenant-a"), "txn-checkout-duplicate").orElseThrow().state());
    }

    @Test
    void providerFailureIsDurablyProjectedAndCannotReportCheckoutSuccess() {
        provider.failCheckout = true;
        assertThrows(IllegalStateException.class, () -> authority.initiateCheckout(
                checkout("tenant-a", "txn-provider-fail", "checkout-provider-fail", "provider-fail", 500)));
        assertEquals(PaymentState.FAILED,
                authority.find(principal("tenant-a"), "txn-provider-fail").orElseThrow().state());
        assertEquals(1, provider.checkoutCalls.get());
    }

    @Test
    void providerResultExactReplayIsStableAndDifferentResultFailsClosed() {
        PaymentTransaction transaction = authority.initiateCheckout(
                checkout("tenant-a", "txn-bind", "checkout-bind", "bind-key", 300));
        authority.bindProviderResult(commandForBind(transaction, "stripe-ref-checkout-bind", "https://pay/checkout-bind"));
        authority.bindProviderResult(commandForBind(transaction, "stripe-ref-checkout-bind", "https://pay/checkout-bind"));
        assertThrows(IllegalStateException.class, () -> authority.bindProviderResult(
                commandForBind(transaction, "different-ref", "https://pay/checkout-bind")));
    }

    @Test
    void legalTransitionsUseOptimisticCasAndTerminalStateDoesNotRegress() {
        PaymentTransaction pending = authority.initiateCheckout(
                checkout("tenant-a", "txn-state", "checkout-state", "state-key", 700));
        PaymentTransaction settled = authority.applyWebhook(webhook(
                pending, "evt-new", 20, PaymentState.SETTLED, "digest-new", pending.version()));
        assertEquals(PaymentState.SETTLED, settled.state());
        PaymentTransaction stale = authority.applyWebhook(webhook(
                settled, "evt-old", 10, PaymentState.PENDING, "digest-old", settled.version()));
        assertEquals(PaymentState.SETTLED, stale.state());
        assertThrows(IllegalStateException.class, () -> authority.verifyPayment(new VerifyPaymentCommand(
                settled.principal(), settled.transactionId(), "stripe", settled.providerReference(),
                pending.version(), "stale-verify", "api", "verify", "trace-stale", NOW)));
    }

    @Test
    void webhookExactReplayMismatchAndConcurrentDuplicateProduceOneReceiptProjectionAndOutbox() throws Exception {
        PaymentTransaction pending = authority.initiateCheckout(
                checkout("tenant-a", "txn-webhook", "checkout-webhook", "webhook-key", 800));
        ApplyWebhookCommand event = webhook(pending, "evt-concurrent", 1,
                PaymentState.SETTLED, "same-digest", pending.version());
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var one = pool.submit(() -> { start.await(); return authority.applyWebhook(event); });
        var two = pool.submit(() -> { start.await(); return authority.applyWebhook(event); });
        start.countDown();
        assertEquals(PaymentState.SETTLED, one.get(10, TimeUnit.SECONDS).state());
        assertEquals(PaymentState.SETTLED, two.get(10, TimeUnit.SECONDS).state());
        pool.shutdownNow();
        assertEquals(1, count("provider_webhook_receipt"));
        assertEquals(1, count("payment_outbox"));
        assertEquals(pending.version() + 1,
                jdbc.queryForObject("SELECT version FROM payment_transaction WHERE id='txn-webhook'", Long.class));
        assertThrows(IllegalStateException.class, () -> authority.applyWebhook(webhook(
                pending, "evt-concurrent", 1, PaymentState.FAILED, "different-digest", pending.version())));
    }

    @Test
    void webhookReceiptProjectionAndOutboxRollBackTogether() {
        PaymentTransaction pending = authority.initiateCheckout(
                checkout("tenant-a", "txn-rollback", "checkout-rollback", "rollback-key", 900));
        installFailureTrigger("fail_payment_outbox", "payment_outbox", "BEFORE", "INSERT");
        assertThrows(RuntimeException.class, () -> authority.applyWebhook(webhook(
                pending, "evt-rollback", 1, PaymentState.SETTLED, "digest-rollback", pending.version())));
        assertEquals(0, count("provider_webhook_receipt"));
        assertEquals(PaymentState.PENDING,
                authority.find(principal("tenant-a"), "txn-rollback").orElseThrow().state());
    }

    @Test
    void durableCheckoutBindingSurvivesAuthorityRestartAndIsTenantScoped() {
        PaymentTransaction created = authority.initiateCheckout(
                checkout("tenant-a", "txn-restart", "checkout-restart", "restart-key", 100));
        PaymentTransactionAuthority restarted = new PaymentTransactionAuthority(
                java.util.List.of(provider), new PaymentTransactionJdbcRepository(jdbc),
                context.getBean(PlatformTransactionManager.class));
        assertEquals(created.transactionId(), restarted.findByCheckout(
                principal("tenant-a"), "checkout-restart").orElseThrow().transactionId());
        assertEquals(0, restarted.findByCheckout(principal("tenant-b"), "checkout-restart").stream().count());
    }

    @Test
    void refundReplayCurrencyAndCumulativeGuardsAndProviderFailure() {
        PaymentTransaction settled = settle("txn-refund", "checkout-refund", 1000);
        RefundPaymentCommand refund = refund(settled, "refund-one", 400, "USD", settled.version());
        assertEquals(400, authority.refund(refund).refundedAmount().amountMinor());
        assertEquals(400, authority.refund(refund).refundedAmount().amountMinor());
        assertEquals(1, provider.refundCalls.get());
        assertThrows(IllegalStateException.class, () -> authority.refund(
                refund(settled, "refund-one", 401, "USD", settled.version())));
        assertThrows(IllegalArgumentException.class, () -> authority.refund(
                refund(authority.find(principal("tenant-a"), "txn-refund").orElseThrow(),
                        "refund-eur", 1, "EUR", authority.find(principal("tenant-a"), "txn-refund")
                                .orElseThrow().version())));
        PaymentTransaction current = authority.find(principal("tenant-a"), "txn-refund").orElseThrow();
        assertThrows(IllegalStateException.class, () -> authority.refund(
                refund(current, "refund-over", 601, "USD", current.version())));

        PaymentTransaction failTarget = settle("txn-refund-fail", "checkout-refund-fail", 500);
        provider.failRefund = true;
        assertThrows(IllegalStateException.class, () -> authority.refund(
                refund(failTarget, "refund-provider-fail", 200, "USD", failTarget.version())));
        assertEquals(0, authority.find(principal("tenant-a"), "txn-refund-fail")
                .orElseThrow().refundedAmount().amountMinor());
    }

    @Test
    void concurrentRefundsCannotExceedCapturedAmount() throws Exception {
        PaymentTransaction settled = settle("txn-concurrent-refund", "checkout-concurrent-refund", 1000);
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var one = pool.submit(() -> executeRefund(start,
                refund(settled, "refund-a", 700, "USD", settled.version())));
        var two = pool.submit(() -> executeRefund(start,
                refund(settled, "refund-b", 700, "USD", settled.version())));
        start.countDown();
        int successes = (one.get(10, TimeUnit.SECONDS) ? 1 : 0) + (two.get(10, TimeUnit.SECONDS) ? 1 : 0);
        pool.shutdownNow();
        assertEquals(1, successes);
        assertEquals(700, authority.find(principal("tenant-a"), "txn-concurrent-refund")
                .orElseThrow().refundedAmount().amountMinor());
    }

    @Test
    void concurrentExactRefundDuplicateClaimsOneProviderCall() throws Exception {
        PaymentTransaction settled = settle("txn-duplicate-refund", "checkout-duplicate-refund", 1000);
        RefundPaymentCommand command = refund(settled, "refund-duplicate", 300, "USD", settled.version());
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var one = pool.submit(() -> executeRefund(start, command));
        var two = pool.submit(() -> executeRefund(start, command));
        start.countDown();
        int successes = (one.get(10, TimeUnit.SECONDS) ? 1 : 0) + (two.get(10, TimeUnit.SECONDS) ? 1 : 0);
        pool.shutdownNow();
        org.junit.jupiter.api.Assertions.assertTrue(successes >= 1);
        assertEquals(1, provider.refundCalls.get());
        assertEquals(1, count("payment_refund"));
        assertEquals(300, authority.refund(command).refundedAmount().amountMinor());
    }

    @Test
    void refundProjectionRollbackKeepsCapturedAuthorityAndRetryReusesDurableIntent() {
        PaymentTransaction settled = settle("txn-refund-rollback", "checkout-refund-rollback", 500);
        RefundPaymentCommand command = refund(settled, "refund-rollback", 200, "USD", settled.version());
        installFailureTrigger("fail_payment_refund_projection", "payment_transaction", "BEFORE", "UPDATE");
        assertThrows(RuntimeException.class, () -> authority.refund(command));
        assertEquals(0, authority.find(principal("tenant-a"), "txn-refund-rollback")
                .orElseThrow().refundedAmount().amountMinor());
        assertEquals("FAILED", jdbc.queryForObject(
                "SELECT refund_state FROM payment_refund WHERE idempotency_key='refund-rollback'", String.class));

        jdbc.execute("DROP TRIGGER fail_payment_refund_projection ON payment_transaction");
        assertEquals(200, authority.refund(command).refundedAmount().amountMinor());
        assertEquals(2, provider.refundCalls.get());
    }

    @Test
    void tenantAndFullPrincipalScopeAreMandatoryForReadsAndMutations() {
        PaymentTransaction created = authority.initiateCheckout(
                checkout("tenant-a", "txn-isolation", "checkout-isolation", "isolation-key", 100));
        assertEquals(0, authority.find(principal("tenant-b"), created.transactionId()).stream().count());
        PrincipalRef otherWorkspace = new PrincipalRef("tenant-a", PrincipalType.USER,
                "user-1", "workspace-other", null);
        assertEquals(0, authority.find(otherWorkspace, created.transactionId()).stream().count());
        assertThrows(IllegalStateException.class, () -> authority.applyWebhook(new ApplyWebhookCommand(
                otherWorkspace, created.transactionId(), "stripe", "evt-scope", created.providerReference(),
                "payment.succeeded", 1, PaymentState.SETTLED, "digest-scope", created.version(),
                "webhook", "provider event", "trace-scope", NOW, NOW, true)));
    }

    private static boolean executeRefund(CountDownLatch start, RefundPaymentCommand command) throws Exception {
        start.await();
        try { authority.refund(command); return true; }
        catch (IllegalStateException expected) { return false; }
    }

    private static boolean executeCheckout(CountDownLatch start, InitiateCheckoutCommand command) throws Exception {
        start.await();
        try { authority.initiateCheckout(command); return true; }
        catch (IllegalStateException expected) { return false; }
    }

    private static PaymentTransaction settle(String transactionId, String checkoutId, long amount) {
        PaymentTransaction pending = authority.initiateCheckout(
                checkout("tenant-a", transactionId, checkoutId, "key-" + transactionId, amount));
        return authority.applyWebhook(webhook(pending, "evt-" + transactionId, 1,
                PaymentState.SETTLED, "digest-" + transactionId, pending.version()));
    }

    private static InitiateCheckoutCommand checkout(
            String tenant, String transactionId, String checkoutId, String key, long amount) {
        return new InitiateCheckoutCommand(transactionId, principal(tenant), "order-" + checkoutId,
                checkoutId, "stripe", new Money(amount, "USD"), "product-ref",
                "https://ok", "https://cancel", key, "commerce", "checkout",
                "trace-" + checkoutId, NOW);
    }

    private static com.example.platform.payment.domain.BindProviderResultCommand commandForBind(
            PaymentTransaction transaction, String providerReference, String redirectUrl) {
        return new com.example.platform.payment.domain.BindProviderResultCommand(
                transaction.principal(), transaction.transactionId(), "stripe", providerReference,
                redirectUrl, PaymentState.PENDING, transaction.version(),
                "bind:" + transaction.transactionId(), "provider", "bind result",
                transaction.traceId(), NOW);
    }

    private static ApplyWebhookCommand webhook(PaymentTransaction transaction, String eventId,
                                                long cursor, PaymentState state, String digest,
                                                long expectedVersion) {
        return new ApplyWebhookCommand(transaction.principal(), transaction.transactionId(), "stripe",
                eventId, transaction.providerReference(), "payment.succeeded", cursor, state, digest,
                expectedVersion, "webhook", "provider event", "trace-webhook", NOW, NOW, true);
    }

    private static RefundPaymentCommand refund(PaymentTransaction transaction, String key,
                                                long amount, String currency, long version) {
        return new RefundPaymentCommand(transaction.principal(), transaction.transactionId(),
                transaction.providerReference(), new Money(amount, currency), version, key,
                "api", "customer refund", "trace-refund", NOW);
    }

    private static PrincipalRef principal(String tenant) {
        return new PrincipalRef(tenant, PrincipalType.USER, "user-1", "workspace-1", null);
    }

    private static int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static void installFailureTrigger(String trigger, String table, String timing, String operation) {
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_payment_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced'; END $$");
        jdbc.execute("CREATE TRIGGER " + trigger + " " + timing + " " + operation + " ON " + table
                + " FOR EACH ROW EXECUTE FUNCTION fail_payment_test()");
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        static DataSource dataSource;
        @Bean DataSource dataSource() { return dataSource; }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean PaymentTransactionJdbcRepository repository(JdbcTemplate jdbc) {
            return new PaymentTransactionJdbcRepository(jdbc);
        }
        @Bean RecordingProvider provider() { return new RecordingProvider(); }
        @Bean PaymentTransactionAuthority authority(RecordingProvider provider,
                PaymentTransactionJdbcRepository repository, PlatformTransactionManager transactions) {
            return new PaymentTransactionAuthority(java.util.List.of(provider), repository, transactions);
        }
    }

    static final class RecordingProvider implements PaymentProvider {
        final AtomicInteger checkoutCalls = new AtomicInteger();
        final AtomicInteger refundCalls = new AtomicInteger();
        volatile boolean failCheckout;
        volatile boolean failRefund;

        void reset() { checkoutCalls.set(0); refundCalls.set(0); failCheckout = false; failRefund = false; }
        @Override public ProviderCode code() { return new ProviderCode("stripe"); }
        @Override public CheckoutResult createCheckout(InitiateCheckoutCommand command) {
            checkoutCalls.incrementAndGet();
            if (failCheckout) throw new IllegalStateException("provider checkout failed");
            return new CheckoutResult("stripe-ref-" + command.checkoutSessionId(),
                    "https://pay/" + command.checkoutSessionId());
        }
        @Override public PaymentVerificationResult verifyPayment(ProviderVerificationRequest command) {
            return new PaymentVerificationResult(true, "succeeded", PaymentState.SETTLED);
        }
        @Override public ProviderRefundResult refund(ProviderRefundRequest command) {
            refundCalls.incrementAndGet();
            if (failRefund) throw new IllegalStateException("provider refund failed");
            return new ProviderRefundResult(true, "refund-ref-" + command.idempotencyKey(), "succeeded");
        }
        @Override public WebhookParseResult parseWebhook(Map<String, String> headers, String body) {
            throw new UnsupportedOperationException();
        }
    }
}
