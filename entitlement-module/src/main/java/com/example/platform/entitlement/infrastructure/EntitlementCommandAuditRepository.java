package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.EntitlementCommandResult;
import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import com.example.platform.entitlement.domain.EntitlementGrantView;
import com.example.platform.shared.Jsons;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable idempotency claim and transition audit subordinate to EntitlementService. */
@Repository
public class EntitlementCommandAuditRepository {
    private final JdbcTemplate jdbc;

    public EntitlementCommandAuditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean claim(String commandId, EntitlementGrantCommand command, Instant now) {
        return jdbc.update("""
                INSERT INTO entitlement_command_audit
                    (id, tenant_id, principal_type, principal_id, idempotency_key,
                     command_type, payload_fingerprint, actor, reason, trace_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """, commandId, command.principal().tenantId(), command.principal().principalType().name(),
                command.principal().principalId(), command.idempotencyKey(), command.commandType().name(),
                command.fingerprint(), command.actor(), command.reason(), command.traceId(),
                Timestamp.from(now)) == 1;
    }

    public EntitlementCommandResult replay(EntitlementGrantCommand command) {
        List<EntitlementCommandResult> rows = jdbc.query("""
                SELECT id, payload_fingerprint, result_snapshot
                FROM entitlement_command_audit WHERE tenant_id = ? AND idempotency_key = ?
                """, (rs, row) -> {
                    if (!command.fingerprint().equals(rs.getString("payload_fingerprint"))) {
                        throw new IllegalStateException(
                                "Idempotency key reused with different entitlement command payload");
                    }
                    String snapshot = rs.getString("result_snapshot");
                    if (snapshot == null) throw new IllegalStateException("Entitlement command has no committed result");
                    return new EntitlementCommandResult(rs.getString("id"),
                            Jsons.fromJson(snapshot, EntitlementGrantView.class));
                }, command.principal().tenantId(), command.idempotencyKey());
        if (rows.size() != 1) throw new IllegalStateException("Entitlement command claim not found");
        return rows.get(0);
    }

    public void complete(String commandId, EntitlementGrantView result, Instant completedAt) {
        int updated = jdbc.update("""
                UPDATE entitlement_command_audit SET result_snapshot = ?, completed_at = ?
                WHERE id = ? AND result_snapshot IS NULL
                """, Jsons.toJson(result), Timestamp.from(completedAt), commandId);
        if (updated != 1) throw new IllegalStateException("Entitlement command audit completion failed");
    }
}
