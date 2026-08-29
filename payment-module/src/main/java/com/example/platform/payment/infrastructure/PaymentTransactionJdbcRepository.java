package com.example.platform.payment.infrastructure;

import com.example.platform.payment.domain.ApplyWebhookCommand;
import com.example.platform.payment.domain.BindProviderResultCommand;
import com.example.platform.payment.domain.InitiateCheckoutCommand;
import com.example.platform.payment.domain.PaymentState;
import com.example.platform.payment.domain.PaymentTransaction;
import com.example.platform.payment.domain.RefundPaymentCommand;
import com.example.platform.payment.domain.RefundResult;
import com.example.platform.payment.domain.VerifyPaymentCommand;
import com.example.platform.payment.domain.PaymentVerificationResult;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sole physical SQL writer for Payment transactions, commands, receipts, refunds and outbox. */
@Repository
public class PaymentTransactionJdbcRepository {

    private final JdbcTemplate jdbc;

    public PaymentTransactionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockIdentity(String scope, String key) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", scope + ":" + key);
    }

    public Optional<StoredCommand> findCommand(PrincipalRef principal, String idempotencyKey) {
        return jdbc.query("""
                SELECT transaction_id, payload_fingerprint, result_fingerprint,
                       result_state, result_version
                FROM payment_command WHERE tenant_id = ? AND idempotency_key = ?
                  AND principal_type = ? AND principal_id = ?
                  AND workspace_id = ? AND organization_id = ?
                """, (rs, row) -> new StoredCommand(rs.getString("transaction_id"),
                rs.getString("payload_fingerprint"), rs.getString("result_fingerprint"),
                PaymentState.valueOf(rs.getString("result_state")), rs.getLong("result_version")),
                principal.tenantId(), idempotencyKey, principal.principalType().name(),
                principal.principalId(), normalize(principal.workspaceId()),
                normalize(principal.organizationId())).stream().findFirst();
    }

    public PaymentTransaction insertIntent(InitiateCheckoutCommand command) {
        String workspace = normalize(command.principal().workspaceId());
        String organization = normalize(command.principal().organizationId());
        jdbc.update("""
                INSERT INTO payment_transaction
                (id, tenant_id, principal_type, principal_id, workspace_id, organization_id,
                 order_id, checkout_session_id, provider_code, provider_reference, redirect_url,
                 amount_minor, currency_code, transaction_state, provider_event_cursor,
                 captured_amount_minor, refunded_amount_minor, version, source, trace_id,
                 created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, 'INITIATED', NULL,
                        0, 0, 1, ?, ?, ?, ?)
                """, command.transactionId(), command.principal().tenantId(),
                command.principal().principalType().name(), command.principal().principalId(),
                workspace, organization, command.orderId(), command.checkoutSessionId(),
                command.providerCode(), command.amount().amountMinor(), command.amount().currency(),
                command.source(), command.traceId(), Timestamp.from(command.occurredAt()),
                Timestamp.from(command.occurredAt()));
        saveCommand(command.principal(), command.idempotencyKey(), "INITIATE",
                command.transactionId(), command.fingerprint(), null, PaymentState.INITIATED, 1,
                command.source(), command.reason(), command.traceId(), command.occurredAt());
        return find(command.principal(), command.transactionId()).orElseThrow();
    }

    public PaymentTransaction bindProviderResult(BindProviderResultCommand command) {
        PaymentTransaction current = requireForUpdate(command.principal(), command.transactionId());
        if (!current.providerCode().equals(command.providerCode())) {
            throw new IllegalStateException("Payment provider mismatch");
        }
        if (current.providerReference() != null) {
            if (!current.providerReference().equals(command.providerReference())
                    || !optional(current.redirectUrl()).equals(optional(command.redirectUrl()))) {
                throw new IllegalStateException("Provider result differs from durable binding");
            }
            return current;
        }
        if (current.version() != command.expectedVersion() || current.state() != PaymentState.INITIATED) {
            throw new IllegalStateException("Provider result CAS or legal transition rejected");
        }
        int updated = jdbc.update("""
                UPDATE payment_transaction
                SET provider_reference = ?, redirect_url = ?, transaction_state = ?,
                    provider_call_claimed_at = NULL, version = version + 1, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND version = ? AND transaction_state = 'INITIATED'
                """, command.providerReference(), command.redirectUrl(), command.state().name(),
                Timestamp.from(command.occurredAt()), command.principal().tenantId(),
                command.transactionId(), command.expectedVersion());
        if (updated != 1) throw new IllegalStateException("Provider result CAS rejected");
        return find(command.principal(), command.transactionId()).orElseThrow();
    }

    public PaymentTransaction projectProviderFailure(PrincipalRef principal, String transactionId,
                                                     long expectedVersion, Instant at) {
        requireForUpdate(principal, transactionId);
        int updated = jdbc.update("""
                UPDATE payment_transaction SET transaction_state = 'FAILED', provider_call_claimed_at = NULL,
                    version = version + 1, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND version = ? AND transaction_state = 'INITIATED'
                """, Timestamp.from(at), principal.tenantId(), transactionId, expectedVersion);
        if (updated != 1) throw new IllegalStateException("Provider failure projection CAS rejected");
        return find(principal, transactionId).orElseThrow();
    }

    public boolean claimCheckoutProviderCall(PrincipalRef principal, String transactionId,
                                             long expectedVersion, Instant at) {
        return jdbc.update("""
                UPDATE payment_transaction SET provider_call_claimed_at = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND principal_type = ? AND principal_id = ?
                  AND workspace_id = ? AND organization_id = ?
                  AND version = ? AND transaction_state = 'INITIATED'
                  AND provider_call_claimed_at IS NULL
                """, Timestamp.from(at), Timestamp.from(at), principal.tenantId(), transactionId,
                principal.principalType().name(), principal.principalId(),
                normalize(principal.workspaceId()), normalize(principal.organizationId()),
                expectedVersion) == 1;
    }

    public void releaseCheckoutProviderCall(PrincipalRef principal, String transactionId,
                                            long expectedVersion, Instant at) {
        jdbc.update("""
                UPDATE payment_transaction SET provider_call_claimed_at = NULL, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND principal_type = ? AND principal_id = ?
                  AND workspace_id = ? AND organization_id = ?
                  AND version = ? AND transaction_state = 'INITIATED'
                """, Timestamp.from(at), principal.tenantId(), transactionId,
                principal.principalType().name(), principal.principalId(),
                normalize(principal.workspaceId()), normalize(principal.organizationId()),
                expectedVersion);
    }

    public PaymentTransaction projectVerification(VerifyPaymentCommand command,
                                                  PaymentVerificationResult verification) {
        PaymentTransaction current = requireForUpdate(command.principal(), command.transactionId());
        if (!current.providerCode().equals(command.providerCode())
                || !current.providerReference().equals(command.providerReference())) {
            throw new IllegalStateException("Verification transaction binding mismatch");
        }
        if (current.version() != command.expectedVersion()) {
            throw new IllegalStateException("Verification optimistic CAS rejected");
        }
        if (current.state().terminalForProviderProjection()) {
            if (current.state() == verification.canonicalState()) return current;
            throw new IllegalStateException("Verification cannot regress terminal payment state");
        }
        requireLegalProviderTransition(current.state(), verification.canonicalState());
        long captured = verification.canonicalState() == PaymentState.SETTLED
                ? current.amount().amountMinor() : current.capturedAmount().amountMinor();
        int updated = jdbc.update("""
                UPDATE payment_transaction SET transaction_state = ?, captured_amount_minor = ?,
                    version = version + 1, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND version = ? AND transaction_state = ?
                """, verification.canonicalState().name(), captured,
                Timestamp.from(command.occurredAt()), command.principal().tenantId(),
                command.transactionId(), command.expectedVersion(), current.state().name());
        if (updated != 1) throw new IllegalStateException("Verification CAS rejected");
        return find(command.principal(), command.transactionId()).orElseThrow();
    }

    public WebhookReceipt findWebhookReceipt(String providerCode, String eventId) {
        return jdbc.query("""
                SELECT * FROM provider_webhook_receipt WHERE provider_code = ? AND event_id = ?
                """, (rs, row) -> new WebhookReceipt(rs.getString("provider_code"),
                rs.getString("event_id"), rs.getString("payload_sha256"),
                rs.getString("event_type"), rs.getLong("event_cursor"),
                rs.getString("provider_reference"), PaymentState.valueOf(rs.getString("canonical_state")),
                rs.getString("processing_outcome"), rs.getString("transaction_id")),
                providerCode, eventId).stream().findFirst().orElse(null);
    }

    public PaymentTransaction applyWebhook(ApplyWebhookCommand command) {
        PaymentTransaction current = requireForUpdate(command.principal(), command.transactionId());
        if (!current.providerCode().equals(command.providerCode())
                || !command.providerReference().equals(current.providerReference())) {
            throw new IllegalStateException("Webhook transaction binding mismatch");
        }
        String outcome;
        PaymentTransaction result = current;
        Long cursor = current.providerEventCursor();
        if (cursor != null && command.eventCursor() <= cursor) {
            outcome = "IGNORED_STALE";
        } else if (current.state().terminalForProviderProjection()) {
            outcome = "IGNORED_TERMINAL";
        } else {
            if (current.version() != command.expectedVersion()) {
                throw new IllegalStateException("Webhook optimistic CAS rejected");
            }
            requireLegalProviderTransition(current.state(), command.state());
            long captured = command.state() == PaymentState.SETTLED
                    ? current.amount().amountMinor() : current.capturedAmount().amountMinor();
            int updated = jdbc.update("""
                    UPDATE payment_transaction
                    SET transaction_state = ?, provider_event_cursor = ?, captured_amount_minor = ?,
                        version = version + 1, updated_at = ?
                    WHERE tenant_id = ? AND id = ? AND version = ? AND transaction_state = ?
                    """, command.state().name(), command.eventCursor(), captured,
                    Timestamp.from(command.receivedAt()), command.principal().tenantId(),
                    command.transactionId(), command.expectedVersion(), current.state().name());
            if (updated != 1) throw new IllegalStateException("Webhook CAS rejected");
            result = find(command.principal(), command.transactionId()).orElseThrow();
            outcome = "PROJECTED";
            if (command.state() == PaymentState.SETTLED) insertSettlementOutbox(result, command);
        }
        insertWebhookReceipt(command, outcome);
        return result;
    }

    private void insertWebhookReceipt(ApplyWebhookCommand command, String outcome) {
        jdbc.update("""
                INSERT INTO provider_webhook_receipt
                (id, tenant_id, provider_code, event_id, payload_sha256, event_type, event_cursor,
                 provider_reference, canonical_state, processing_outcome, transaction_id,
                 occurred_at, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "pwr_" + shortHash(command.fingerprint()), command.principal().tenantId(),
                command.providerCode(), command.eventId(),
                command.payloadSha256(), command.eventType(), command.eventCursor(),
                command.providerReference(), command.state().name(), outcome, command.transactionId(),
                Timestamp.from(command.occurredAt()), Timestamp.from(command.receivedAt()));
    }

    private void insertSettlementOutbox(PaymentTransaction transaction, ApplyWebhookCommand command) {
        jdbc.update("""
                INSERT INTO payment_outbox
                (id, tenant_id, event_type, aggregate_id, dedupe_key, provider_code,
                 provider_reference, checkout_session_id, trace_id, created_at, dispatched_at)
                VALUES (?, ?, 'PAYMENT_SETTLED', ?, ?, ?, ?, ?, ?, ?, NULL)
                """, "pob_" + shortHash(command.fingerprint()), transaction.principal().tenantId(),
                transaction.transactionId(), command.providerCode() + ":" + command.eventId(),
                command.providerCode(), command.providerReference(), transaction.checkoutSessionId(),
                command.traceId(), Timestamp.from(command.receivedAt()));
    }

    public RefundIntent reserveRefund(RefundPaymentCommand command) {
        PaymentTransaction current = requireForUpdate(command.principal(), command.transactionId());
        if (current.version() != command.expectedVersion()) {
            throw new IllegalStateException("Refund optimistic CAS rejected");
        }
        if (current.state() != PaymentState.SETTLED && current.state() != PaymentState.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Refund requires a settled transaction");
        }
        if (!current.amount().currency().equals(command.amount().currency())) {
            throw new IllegalArgumentException("Refund currency must match captured currency");
        }
        if (!command.originalCaptureReference().equals(current.providerReference())) {
            throw new IllegalStateException("Original capture reference mismatch");
        }
        Long reserved = jdbc.queryForObject("""
                SELECT COALESCE(sum(amount_minor), 0) FROM payment_refund
                WHERE tenant_id = ? AND transaction_id = ?
                  AND refund_state IN ('REQUESTED','PROVIDER_CALLING','SUCCEEDED')
                """, Long.class, command.principal().tenantId(), command.transactionId());
        if (reserved == null || command.amount().amountMinor() > current.capturedAmount().amountMinor() - reserved) {
            throw new IllegalStateException("Cumulative refund exceeds captured amount");
        }
        String refundId = "prf_" + shortHash(
                command.principal().tenantId() + ":" + command.idempotencyKey());
        jdbc.update("""
                INSERT INTO payment_refund
                (id, tenant_id, transaction_id, provider_refund_reference,
                 original_capture_reference, amount_minor, currency_code, refund_state,
                 idempotency_key, payload_fingerprint, source, reason, trace_id, created_at, updated_at)
                VALUES (?, ?, ?, NULL, ?, ?, ?, 'REQUESTED', ?, ?, ?, ?, ?, ?, ?)
                """, refundId, command.principal().tenantId(), command.transactionId(),
                command.originalCaptureReference(), command.amount().amountMinor(), command.amount().currency(),
                command.idempotencyKey(), command.fingerprint(), command.source(), command.reason(),
                command.traceId(), Timestamp.from(command.occurredAt()), Timestamp.from(command.occurredAt()));
        saveCommand(command.principal(), command.idempotencyKey(), "REFUND",
                command.transactionId(), command.fingerprint(), null, current.state(), current.version(),
                command.source(), command.reason(), command.traceId(), command.occurredAt());
        return new RefundIntent(refundId, current);
    }

    public RefundResult completeRefund(RefundPaymentCommand command, String refundId,
                                       String providerRefundReference) {
        PaymentTransaction current = requireForUpdate(command.principal(), command.transactionId());
        int refundUpdated = jdbc.update("""
                UPDATE payment_refund SET refund_state = 'SUCCEEDED', provider_refund_reference = ?,
                    updated_at = ? WHERE tenant_id = ? AND id = ? AND refund_state = 'PROVIDER_CALLING'
                """, providerRefundReference, Timestamp.from(command.occurredAt()),
                command.principal().tenantId(), refundId);
        if (refundUpdated != 1) throw new IllegalStateException("Refund result was already projected differently");
        long refunded = Math.addExact(current.refundedAmount().amountMinor(), command.amount().amountMinor());
        if (refunded > current.capturedAmount().amountMinor()) throw new IllegalStateException("Refund exceeds capture");
        PaymentState target = refunded == current.capturedAmount().amountMinor()
                ? PaymentState.REFUNDED : PaymentState.PARTIALLY_REFUNDED;
        int transactionUpdated = jdbc.update("""
                UPDATE payment_transaction SET refunded_amount_minor = ?, transaction_state = ?,
                    version = version + 1, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND version = ?
                """, refunded, target.name(), Timestamp.from(command.occurredAt()),
                command.principal().tenantId(), command.transactionId(), current.version());
        if (transactionUpdated != 1) throw new IllegalStateException("Refund projection CAS rejected");
        PaymentTransaction result = find(command.principal(), command.transactionId()).orElseThrow();
        updateCommandResult(command.principal(), command.idempotencyKey(),
                providerRefundReference, result.state(), result.version());
        return new RefundResult(refundId, result.transactionId(), result.state(),
                result.refundedAmount(), result.version(), providerRefundReference);
    }

    public void failRefund(RefundPaymentCommand command, String refundId) {
        jdbc.update("""
                UPDATE payment_refund SET refund_state = 'FAILED', updated_at = ?
                WHERE tenant_id = ? AND id = ? AND refund_state = 'PROVIDER_CALLING'
                """, Timestamp.from(command.occurredAt()), command.principal().tenantId(), refundId);
    }

    public Optional<RefundResult> findRefundResult(PrincipalRef principal, String idempotencyKey) {
        return jdbc.query("""
                SELECT r.*, t.transaction_state, t.refunded_amount_minor, t.version
                FROM payment_refund r JOIN payment_transaction t
                  ON t.tenant_id = r.tenant_id AND t.id = r.transaction_id
                WHERE r.tenant_id = ? AND r.idempotency_key = ?
                  AND t.principal_type = ? AND t.principal_id = ?
                  AND t.workspace_id = ? AND t.organization_id = ?
                """, (rs, row) -> new RefundResult(rs.getString("id"), rs.getString("transaction_id"),
                PaymentState.valueOf(rs.getString("transaction_state")),
                new Money(rs.getLong("refunded_amount_minor"), rs.getString("currency_code")),
                rs.getLong("version"), rs.getString("provider_refund_reference")),
                principal.tenantId(), idempotencyKey, principal.principalType().name(),
                principal.principalId(), normalize(principal.workspaceId()),
                normalize(principal.organizationId())).stream().findFirst();
    }

    public Optional<RefundRecord> findRefund(PrincipalRef principal, String idempotencyKey) {
        return jdbc.query("""
                SELECT r.id, r.transaction_id, r.refund_state, r.payload_fingerprint
                FROM payment_refund r JOIN payment_transaction t
                  ON t.tenant_id = r.tenant_id AND t.id = r.transaction_id
                WHERE r.tenant_id = ? AND r.idempotency_key = ?
                  AND t.principal_type = ? AND t.principal_id = ?
                  AND t.workspace_id = ? AND t.organization_id = ?
                """, (rs, row) -> new RefundRecord(rs.getString("id"),
                rs.getString("transaction_id"), rs.getString("refund_state"),
                rs.getString("payload_fingerprint")), principal.tenantId(), idempotencyKey,
                principal.principalType().name(), principal.principalId(),
                normalize(principal.workspaceId()), normalize(principal.organizationId()))
                .stream().findFirst();
    }

    public void resumeRefund(PrincipalRef principal, String refundId, Instant at) {
        jdbc.update("""
                UPDATE payment_refund SET refund_state = 'REQUESTED', provider_call_claimed_at = NULL,
                    updated_at = ?
                WHERE tenant_id = ? AND id = ? AND refund_state = 'FAILED'
                """, Timestamp.from(at), principal.tenantId(), refundId);
    }

    public boolean claimRefundProviderCall(PrincipalRef principal, String refundId, Instant at) {
        return jdbc.update("""
                UPDATE payment_refund SET refund_state = 'PROVIDER_CALLING',
                    provider_call_claimed_at = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND refund_state = 'REQUESTED'
                """, Timestamp.from(at), Timestamp.from(at), principal.tenantId(), refundId) == 1;
    }

    public void saveCommand(PrincipalRef principal, String idempotencyKey, String type,
                            String transactionId, String fingerprint, String resultFingerprint,
                            PaymentState resultState, long resultVersion, String source,
                            String reason, String traceId, Instant at) {
        jdbc.update("""
                INSERT INTO payment_command
                (id, tenant_id, principal_type, principal_id, workspace_id, organization_id,
                 idempotency_key, command_type, transaction_id,
                 payload_fingerprint, result_fingerprint, result_state, result_version,
                 source, reason, trace_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "pcm_" + shortHash(principal.tenantId() + ":" + idempotencyKey),
                principal.tenantId(), principal.principalType().name(), principal.principalId(),
                normalize(principal.workspaceId()), normalize(principal.organizationId()),
                idempotencyKey, type, transactionId, fingerprint, resultFingerprint,
                resultState.name(), resultVersion, source, reason, traceId, Timestamp.from(at));
    }

    public void updateCommandResult(PrincipalRef principal, String idempotencyKey, String resultFingerprint,
                                    PaymentState state, long version) {
        jdbc.update("""
                UPDATE payment_command SET result_fingerprint = ?, result_state = ?, result_version = ?
                WHERE tenant_id = ? AND idempotency_key = ?
                  AND principal_type = ? AND principal_id = ?
                  AND workspace_id = ? AND organization_id = ?
                """, resultFingerprint, state.name(), version, principal.tenantId(), idempotencyKey,
                principal.principalType().name(), principal.principalId(),
                normalize(principal.workspaceId()), normalize(principal.organizationId()));
    }

    public Optional<PaymentTransaction> find(PrincipalRef principal, String transactionId) {
        return jdbc.query("""
                SELECT * FROM payment_transaction
                WHERE tenant_id = ? AND id = ? AND principal_type = ? AND principal_id = ?
                  AND workspace_id = ? AND organization_id = ?
                """, this::mapTransaction, principal.tenantId(), transactionId,
                principal.principalType().name(), principal.principalId(),
                normalize(principal.workspaceId()), normalize(principal.organizationId()))
                .stream().findFirst();
    }

    public Optional<PaymentTransaction> findByCheckout(PrincipalRef principal, String checkoutSessionId) {
        return jdbc.query("""
                SELECT * FROM payment_transaction
                WHERE tenant_id = ? AND checkout_session_id = ?
                  AND principal_type = ? AND principal_id = ?
                  AND workspace_id = ? AND organization_id = ?
                """, this::mapTransaction, principal.tenantId(), checkoutSessionId,
                principal.principalType().name(), principal.principalId(),
                normalize(principal.workspaceId()), normalize(principal.organizationId()))
                .stream().findFirst();
    }

    public Optional<PaymentTransaction> findByProviderReference(String providerCode, String providerReference) {
        return jdbc.query("SELECT * FROM payment_transaction WHERE provider_code = ? AND provider_reference = ?",
                this::mapTransaction, providerCode, providerReference).stream().findFirst();
    }

    public Optional<PaymentOutboxEvent> claimNextOutbox() {
        return jdbc.query("""
                SELECT id, aggregate_id, tenant_id, provider_code, provider_reference,
                       checkout_session_id, trace_id, created_at
                FROM payment_outbox WHERE dispatched_at IS NULL
                ORDER BY created_at, id FOR UPDATE SKIP LOCKED LIMIT 1
                """, (rs, row) -> new PaymentOutboxEvent(rs.getString("id"),
                rs.getString("aggregate_id"), rs.getString("tenant_id"),
                rs.getString("provider_code"), rs.getString("provider_reference"),
                rs.getString("checkout_session_id"), rs.getString("trace_id"),
                rs.getTimestamp("created_at").toInstant())).stream().findFirst();
    }

    public void markOutboxDispatched(String eventId) {
        int updated = jdbc.update("""
                UPDATE payment_outbox SET dispatched_at = now()
                WHERE id = ? AND dispatched_at IS NULL
                """, eventId);
        if (updated != 1) throw new IllegalStateException("Payment outbox event was not pending");
    }

    private PaymentTransaction requireForUpdate(PrincipalRef principal, String transactionId) {
        return jdbc.query("""
                SELECT * FROM payment_transaction
                WHERE tenant_id = ? AND id = ? AND principal_type = ? AND principal_id = ?
                  AND workspace_id = ? AND organization_id = ? FOR UPDATE
                """, this::mapTransaction, principal.tenantId(), transactionId,
                principal.principalType().name(), principal.principalId(),
                normalize(principal.workspaceId()), normalize(principal.organizationId()))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Payment transaction not found for principal"));
    }

    private PaymentTransaction mapTransaction(ResultSet rs, int row) throws SQLException {
        String workspace = denormalize(rs.getString("workspace_id"));
        String organization = denormalize(rs.getString("organization_id"));
        PrincipalRef principal = new PrincipalRef(rs.getString("tenant_id"),
                PrincipalType.valueOf(rs.getString("principal_type")), rs.getString("principal_id"),
                workspace, organization);
        String currency = rs.getString("currency_code");
        return new PaymentTransaction(rs.getString("id"), principal, rs.getString("order_id"),
                rs.getString("checkout_session_id"), rs.getString("provider_code"),
                rs.getString("provider_reference"), rs.getString("redirect_url"),
                new Money(rs.getLong("amount_minor"), currency),
                PaymentState.valueOf(rs.getString("transaction_state")),
                (Long) rs.getObject("provider_event_cursor"),
                new Money(rs.getLong("captured_amount_minor"), currency),
                new Money(rs.getLong("refunded_amount_minor"), currency), rs.getLong("version"),
                rs.getString("source"), rs.getString("trace_id"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static void requireLegalProviderTransition(PaymentState from, PaymentState to) {
        boolean legal = switch (from) {
            case INITIATED -> to == PaymentState.PENDING || to == PaymentState.AUTHORIZED
                    || to == PaymentState.SETTLED || to == PaymentState.FAILED || to == PaymentState.CANCELLED;
            case PENDING -> to == PaymentState.PENDING || to == PaymentState.AUTHORIZED
                    || to == PaymentState.SETTLED || to == PaymentState.FAILED || to == PaymentState.CANCELLED;
            case AUTHORIZED -> to == PaymentState.AUTHORIZED || to == PaymentState.SETTLED
                    || to == PaymentState.FAILED || to == PaymentState.CANCELLED;
            default -> false;
        };
        if (!legal) throw new IllegalStateException("Illegal payment state transition " + from + " -> " + to);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 24);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String normalize(String value) { return value == null ? "" : value; }
    private static String denormalize(String value) { return value == null || value.isEmpty() ? null : value; }
    private static String optional(String value) { return value == null ? "" : value; }

    public record StoredCommand(String transactionId, String fingerprint, String resultFingerprint,
                                PaymentState resultState, long resultVersion) {}
    public record WebhookReceipt(String providerCode, String eventId, String payloadSha256,
                                 String eventType, long eventCursor, String providerReference,
                                 PaymentState state, String outcome, String transactionId) {}
    public record RefundIntent(String refundId, PaymentTransaction transaction) {}
    public record RefundRecord(String refundId, String transactionId, String state, String fingerprint) {}
    public record PaymentOutboxEvent(String eventId, String transactionId, String tenantId,
                                     String providerCode, String providerReference,
                                     String checkoutSessionId, String traceId, Instant createdAt) {}
}
