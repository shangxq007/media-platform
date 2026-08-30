package com.example.platform.storage.infrastructure.identity;

import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.app.identity.StorageIssuanceConflictException;
import com.example.platform.storage.app.identity.StorageWriteIntentRepository;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.domain.identity.StorageWriteIntent;
import com.example.platform.storage.domain.identity.StorageWriteIntent.State;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL persistence for one durable write intent per full-owner issuance key. */
@Repository
public class JdbcStorageWriteIntentRepository implements StorageWriteIntentRepository {

    private static final long LOCK_DOMAIN = 0x53544f52414745L;

    private final JdbcTemplate jdbc;

    public JdbcStorageWriteIntentRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void lockOwnerKey(StorageOwnershipScope owner, IssuanceIdempotencyKey key) {
        String project = owner.projectId();
        String projectComponent = project == null
                ? "project:null"
                : "project:value:" + project.length() + ":" + project;
        String lockInput = "storage-write-intent-owner-key-v1|tenant:value:"
                + owner.tenantId().length() + ":" + owner.tenantId()
                + "|" + projectComponent
                + "|key:value:" + key.value().length() + ":" + key.value();
        advisoryLock(lockInput);
    }

    @Override
    public void lockWriteIntent(String writeIntentId) {
        advisoryLock("storage-write-intent-id|" + writeIntentId);
    }

    private void advisoryLock(String lockInput) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select pg_advisory_xact_lock(hashtextextended(?, ?))")) {
                statement.setString(1, lockInput);
                statement.setLong(2, LOCK_DOMAIN);
                statement.execute();
            }
            return null;
        });
    }

    @Override
    public Optional<StorageWriteIntent> findByOwnerKey(
            StorageOwnershipScope owner, IssuanceIdempotencyKey key) {
        return query("where tenant_id = ? and project_id is not distinct from ? "
                        + "and issuance_idempotency_key = ?",
                owner.tenantId(), owner.projectId(), key.value());
    }

    @Override
    public Optional<StorageWriteIntent> findById(String writeIntentId) {
        return query("where write_intent_id = ?", writeIntentId);
    }

    @Override
    public void create(StorageWriteIntent intent) {
        OffsetDateTime createdAt = utc(intent.createdAt());
        jdbc.update("""
                insert into storage_logical_object (
                    object_id, tenant_id, project_id, issuance_idempotency_key,
                    semantic_fingerprint, created_at
                ) values (?, ?, ?, ?, ?, ?)
                """,
                intent.objectId().value(), intent.owner().tenantId(), intent.owner().projectId(),
                intent.idempotencyKey().value(), intent.semanticFingerprint(), createdAt);
        jdbc.update("""
                insert into storage_write_intent (
                    write_intent_id, object_id, tenant_id, project_id,
                    issuance_idempotency_key, semantic_fingerprint, provider_request_id,
                    provider_correlation_id, provider_completion_fingerprint,
                    intent_state, last_error_code,
                    reconciliation_detail, created_at, updated_at, completed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                intent.writeIntentId(), intent.objectId().value(), intent.owner().tenantId(),
                intent.owner().projectId(), intent.idempotencyKey().value(),
                intent.semanticFingerprint(), intent.providerRequestId(),
                intent.providerCorrelationId(), intent.providerCompletionFingerprint(),
                intent.state().name(), intent.lastErrorCode(),
                intent.reconciliationDetail(), createdAt, createdAt, null);
    }

    @Override
    public StorageWriteIntent recordProviderCompleted(
            String writeIntentId, String providerCorrelationId,
            String providerCompletionFingerprint, Instant updatedAt) {
        StorageWriteIntent existing = requireIntent(writeIntentId);
        if (existing.state() == State.CANONICAL_COMMITTED
                || existing.state() == State.PROVIDER_COMPLETED) {
            if (!Objects.equals(existing.providerCorrelationId(), providerCorrelationId)
                    || !Objects.equals(
                            existing.providerCompletionFingerprint(),
                            providerCompletionFingerprint)) {
                throw new StorageIssuanceConflictException(
                        "write intent already carries a different provider correlation");
            }
            return existing;
        }
        if (existing.state() != State.PENDING_PROVIDER) {
            throw new IllegalStateException(
                    "write intent is not eligible for provider completion");
        }
        jdbc.update("""
                update storage_write_intent
                   set provider_correlation_id = ?, intent_state = 'PROVIDER_COMPLETED',
                       provider_completion_fingerprint = ?, updated_at = ?
                 where write_intent_id = ? and intent_state = 'PENDING_PROVIDER'
                """, providerCorrelationId, providerCompletionFingerprint,
                utc(updatedAt), writeIntentId);
        return requireIntent(writeIntentId);
    }

    @Override
    public StorageWriteIntent recordCanonicalCommitted(
            String writeIntentId, Instant completedAt) {
        int changed = jdbc.update("""
                update storage_write_intent
                   set intent_state = 'CANONICAL_COMMITTED', updated_at = ?, completed_at = ?
                 where write_intent_id = ? and intent_state = 'PROVIDER_COMPLETED'
                """, utc(completedAt), utc(completedAt), writeIntentId);
        if (changed != 1) {
            throw new IllegalStateException(
                    "write intent was not provider-completed at canonical commit");
        }
        return requireIntent(writeIntentId);
    }

    private StorageWriteIntent requireIntent(String writeIntentId) {
        return findById(writeIntentId)
                .orElseThrow(() -> new IllegalArgumentException("unknown storage write intent"));
    }

    private Optional<StorageWriteIntent> query(String predicate, Object... arguments) {
        List<StorageWriteIntent> rows = jdbc.query("""
                select write_intent_id, object_id, tenant_id, project_id,
                       issuance_idempotency_key, semantic_fingerprint, provider_request_id,
                       provider_correlation_id, provider_completion_fingerprint,
                       intent_state, last_error_code,
                       reconciliation_detail, created_at, updated_at, completed_at
                  from storage_write_intent
                """ + predicate, (rs, rowNumber) -> {
            OffsetDateTime completed = rs.getObject("completed_at", OffsetDateTime.class);
            return new StorageWriteIntent(
                    rs.getString("write_intent_id"),
                    new StorageObjectId(rs.getString("object_id")),
                    new StorageOwnershipScope(
                            rs.getString("tenant_id"), rs.getString("project_id")),
                    new IssuanceIdempotencyKey(rs.getString("issuance_idempotency_key")),
                    rs.getString("semantic_fingerprint"),
                    rs.getString("provider_request_id"),
                    rs.getString("provider_correlation_id"),
                    rs.getString("provider_completion_fingerprint"),
                    State.valueOf(rs.getString("intent_state")),
                    rs.getString("last_error_code"),
                    rs.getString("reconciliation_detail"),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                    completed == null ? null : completed.toInstant());
        }, arguments);
        if (rows.size() > 1) {
            throw new IllegalStateException("storage write intent lookup was not unique");
        }
        return rows.stream().findFirst();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
