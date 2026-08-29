package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.QuotaOperationKind;
import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageOutcome;
import com.example.platform.entitlement.domain.QuotaUsageQuery;
import com.example.platform.entitlement.domain.QuotaUsageRejectionReason;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import com.example.platform.shared.Ids;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Subordinate persistence boundary used only by the canonical QuotaUsageAuthority. */
@Repository
public class QuotaUsageJdbcRepository {

    private static final String EMPTY_SCOPE = "";

    private final JdbcTemplate jdbc;

    public QuotaUsageJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public QuotaUsageResult apply(QuotaUsageCommand command) {
        Instant recordedAt = Instant.now();
        String operationId = Ids.newId("qop");
        List<String> claimed = jdbc.query("""
                INSERT INTO quota_usage_operation (
                    id, tenant_id, principal_type, principal_id, workspace_scope,
                    organization_scope, quota_key, period_start, period_end,
                    signed_delta, limit_value, idempotency_key, operation_kind,
                    outcome, trace_id, reason, occurred_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                ON CONFLICT (tenant_id, principal_type, principal_id, workspace_scope,
                             organization_scope, idempotency_key)
                DO NOTHING
                RETURNING id
                """,
                (resultSet, rowNumber) -> resultSet.getString("id"),
                operationId,
                command.principal().tenantId(),
                command.principal().principalType().name(),
                command.principal().principalId(),
                workspaceScope(command.principal()),
                organizationScope(command.principal()),
                command.quotaKey(),
                Timestamp.from(command.periodStart()),
                Timestamp.from(command.periodEnd()),
                command.signedDelta(),
                command.limitValue(),
                command.idempotencyKey(),
                command.operationKind().name(),
                command.traceId(),
                command.reason(),
                Timestamp.from(command.occurredAt()),
                Timestamp.from(recordedAt));

        if (claimed.isEmpty()) {
            return findOperation(command);
        }

        Long usageAfter = atomicUpdate(command, recordedAt);
        if (usageAfter == null) {
            usageAfter = conditionalInsert(command, recordedAt);
        }
        if (usageAfter == null && command.signedDelta() >= 0) {
            // A concurrent first writer may have inserted the logical row after our
            // initial UPDATE snapshot. A fresh atomic UPDATE statement completes the race.
            usageAfter = atomicUpdate(command, recordedAt);
        }

        if (usageAfter != null) {
            long usageBefore = Math.subtractExact(usageAfter, command.signedDelta());
            jdbc.update("""
                    UPDATE quota_usage_operation
                    SET outcome = 'APPLIED', usage_before = ?, usage_after = ?, rejection_reason = NULL
                    WHERE id = ?
                    """, usageBefore, usageAfter, operationId);
        } else {
            long current = currentUsage(command.principal(), command.quotaKey(),
                    command.periodStart(), command.periodEnd());
            QuotaUsageRejectionReason rejection = rejectionReason(current, command);
            jdbc.update("""
                    UPDATE quota_usage_operation
                    SET outcome = 'REJECTED', usage_before = ?, usage_after = ?, rejection_reason = ?
                    WHERE id = ?
                    """, current, current, rejection.name(), operationId);
        }

        return findOperation(command);
    }

    public long currentUsage(QuotaUsageQuery query) {
        return currentUsage(query.principal(), query.quotaKey(),
                query.periodStart(), query.periodEnd());
    }

    private Long atomicUpdate(QuotaUsageCommand command, Instant updatedAt) {
        List<Long> rows = jdbc.query("""
                UPDATE quota_usage
                SET usage_value = ((usage_value::numeric + ?::numeric)::bigint),
                    updated_at = ?
                WHERE tenant_id = ?
                  AND principal_type = ?
                  AND principal_id = ?
                  AND workspace_scope = ?
                  AND organization_scope = ?
                  AND quota_key = ?
                  AND period_start = ?
                  AND period_end = ?
                  AND (usage_value::numeric + ?::numeric) >= 0
                  AND (usage_value::numeric + ?::numeric) <= ?::numeric
                RETURNING usage_value
                """,
                (resultSet, rowNumber) -> resultSet.getLong("usage_value"),
                command.signedDelta(),
                Timestamp.from(updatedAt),
                command.principal().tenantId(),
                command.principal().principalType().name(),
                command.principal().principalId(),
                workspaceScope(command.principal()),
                organizationScope(command.principal()),
                command.quotaKey(),
                Timestamp.from(command.periodStart()),
                Timestamp.from(command.periodEnd()),
                command.signedDelta(),
                command.signedDelta(),
                command.limitValue());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long conditionalInsert(QuotaUsageCommand command, Instant recordedAt) {
        List<Long> rows = jdbc.query("""
                INSERT INTO quota_usage (
                    id, tenant_id, principal_type, principal_id, workspace_scope,
                    organization_scope, quota_key, period_start, period_end,
                    usage_value, created_at, updated_at)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE ?::numeric >= 0 AND ?::numeric <= ?::numeric
                ON CONFLICT (tenant_id, principal_type, principal_id, workspace_scope,
                             organization_scope, quota_key, period_start, period_end)
                DO NOTHING
                RETURNING usage_value
                """,
                (resultSet, rowNumber) -> resultSet.getLong("usage_value"),
                Ids.newId("qu"),
                command.principal().tenantId(),
                command.principal().principalType().name(),
                command.principal().principalId(),
                workspaceScope(command.principal()),
                organizationScope(command.principal()),
                command.quotaKey(),
                Timestamp.from(command.periodStart()),
                Timestamp.from(command.periodEnd()),
                command.signedDelta(),
                Timestamp.from(recordedAt),
                Timestamp.from(recordedAt),
                command.signedDelta(),
                command.signedDelta(),
                command.limitValue());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long currentUsage(
            PrincipalRef principal, String quotaKey, Instant periodStart, Instant periodEnd) {
        List<Long> rows = jdbc.query("""
                SELECT usage_value
                FROM quota_usage
                WHERE tenant_id = ?
                  AND principal_type = ?
                  AND principal_id = ?
                  AND workspace_scope = ?
                  AND organization_scope = ?
                  AND quota_key = ?
                  AND period_start = ?
                  AND period_end = ?
                """,
                (resultSet, rowNumber) -> resultSet.getLong("usage_value"),
                principal.tenantId(),
                principal.principalType().name(),
                principal.principalId(),
                workspaceScope(principal),
                organizationScope(principal),
                quotaKey,
                Timestamp.from(periodStart),
                Timestamp.from(periodEnd));
        return rows.isEmpty() ? 0L : rows.get(0);
    }

    private QuotaUsageResult findOperation(QuotaUsageCommand command) {
        List<QuotaUsageResult> rows = jdbc.query("""
                SELECT *,
                       (quota_key = ?
                        AND period_start = ?
                        AND period_end = ?
                        AND signed_delta = ?
                        AND limit_value = ?
                        AND operation_kind = ?) AS semantic_payload_matches
                FROM quota_usage_operation
                WHERE tenant_id = ?
                  AND principal_type = ?
                  AND principal_id = ?
                  AND workspace_scope = ?
                  AND organization_scope = ?
                  AND idempotency_key = ?
                """,
                (resultSet, rowNumber) -> {
                    if (!resultSet.getBoolean("semantic_payload_matches")) {
                        throw new IllegalStateException(
                                "Idempotency key reused with different quota command payload");
                    }
                    return mapResult(resultSet, rowNumber);
                },
                command.quotaKey(),
                Timestamp.from(command.periodStart()),
                Timestamp.from(command.periodEnd()),
                command.signedDelta(),
                command.limitValue(),
                command.operationKind().name(),
                command.principal().tenantId(),
                command.principal().principalType().name(),
                command.principal().principalId(),
                workspaceScope(command.principal()),
                organizationScope(command.principal()),
                command.idempotencyKey());
        if (rows.size() != 1) {
            throw new IllegalStateException("Committed quota operation was not found");
        }
        return rows.get(0);
    }

    private QuotaUsageResult mapResult(ResultSet resultSet, int rowNumber) throws SQLException {
        String workspace = resultSet.getString("workspace_scope");
        String organization = resultSet.getString("organization_scope");
        PrincipalRef principal = new PrincipalRef(
                resultSet.getString("tenant_id"),
                PrincipalType.valueOf(resultSet.getString("principal_type")),
                resultSet.getString("principal_id"),
                workspace.isEmpty() ? null : workspace,
                organization.isEmpty() ? null : organization);
        String rejection = resultSet.getString("rejection_reason");
        return new QuotaUsageResult(
                resultSet.getString("id"),
                principal,
                resultSet.getString("quota_key"),
                resultSet.getTimestamp("period_start").toInstant(),
                resultSet.getTimestamp("period_end").toInstant(),
                resultSet.getLong("signed_delta"),
                resultSet.getLong("limit_value"),
                resultSet.getString("idempotency_key"),
                QuotaOperationKind.valueOf(resultSet.getString("operation_kind")),
                QuotaUsageOutcome.valueOf(resultSet.getString("outcome")),
                resultSet.getLong("usage_before"),
                resultSet.getLong("usage_after"),
                rejection == null ? null : QuotaUsageRejectionReason.valueOf(rejection),
                resultSet.getString("trace_id"),
                resultSet.getString("reason"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private static QuotaUsageRejectionReason rejectionReason(
            long current, QuotaUsageCommand command) {
        BigInteger resulting = BigInteger.valueOf(current)
                .add(BigInteger.valueOf(command.signedDelta()));
        if (resulting.signum() < 0) {
            return QuotaUsageRejectionReason.NEGATIVE_RESULT;
        }
        return QuotaUsageRejectionReason.LIMIT_EXCEEDED;
    }

    private static String workspaceScope(PrincipalRef principal) {
        return principal.workspaceId() == null ? EMPTY_SCOPE : principal.workspaceId();
    }

    private static String organizationScope(PrincipalRef principal) {
        return principal.organizationId() == null ? EMPTY_SCOPE : principal.organizationId();
    }
}
