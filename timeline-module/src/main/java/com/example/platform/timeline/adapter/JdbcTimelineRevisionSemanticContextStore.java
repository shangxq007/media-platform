package com.example.platform.timeline.adapter;

import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import com.example.platform.timeline.version.TimelineRevisionSemanticContextJsonCodec;
import com.example.platform.timeline.version.TimelineRevisionSemanticContextStore;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;

/**
 * ROADMAP20 FINAL (F1): DURABLE revision semantic context store — revctx_
 * rows in the EXISTING {@code timeline_snapshot} table (V1-only Flyway
 * governance; zero new migrations).
 *
 * <p>Ownership (REVISION_SEMANTIC_CONTEXT_HAS_EXPLICIT_OWNERSHIP_V1): every
 * row is bound to (project_id, tenant_id). {@code storeTx} writes the
 * explicit tenant (never null); {@code findByRevisionId} resolves ONLY within
 * the supplied (projectId, tenantId) — cross-project / cross-tenant
 * attachment is impossible.
 *
 * <p>Corruption / immutability: an existing revctx_ row is validated by FULL
 * deserialization (codec recomputes the revision semantic digest) + explicit
 * ownership match. Partial JSON field extraction is never authority.
 * Exact identical context -> idempotent PASS; any difference (project,
 * tenant, digest, Effect pin, timeline digest) -> FAIL CLOSED.
 */
public final class JdbcTimelineRevisionSemanticContextStore
        implements TimelineRevisionSemanticContextStore {

    public static final String ID_PREFIX = "revctx_";

    private final DSLContext dsl;

    public JdbcTimelineRevisionSemanticContextStore(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public void storeTx(DSLContext tx, String projectId, String tenantId,
                        String revisionId, TimelineRevisionSemanticContext context) {
        Objects.requireNonNull(tx, "tx");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(context, "context");
        String id = ID_PREFIX + revisionId;
        String payload = TimelineRevisionSemanticContextJsonCodec.serialize(context);
        String existingProject = tx.fetch(
                        "select project_id from timeline_snapshot where id = ?", id)
                .getValues(0, String.class).stream().findFirst().orElse(null);
        if (existingProject != null) {
            validateExistingRow(id, projectId, tenantId, context, tx);
            return; // exact identical -> idempotent PASS
        }
        tx.execute("insert into timeline_snapshot "
                        + "(id, project_id, tenant_id, payload_json, schema_version, created_at) "
                        + "values (?, ?, ?, ?, ?, now())",
                id, projectId, tenantId, payload, context.digestContractVersion());
    }

    /** F1/F4-§36: full deserialize + ownership + digest verification of an existing revctx row. */
    private void validateExistingRow(String id, String requestedProject, String requestedTenant,
                                     TimelineRevisionSemanticContext incoming, DSLContext tx) {
        org.jooq.Record rec = tx.fetchOne(
                "select project_id, tenant_id, payload_json from timeline_snapshot where id = ?", id);
        String storedProject = rec == null ? null : rec.get(0, String.class);
        String storedTenant = rec == null ? null : rec.get(1, String.class);
        String storedPayload = rec == null ? null : rec.get(2, String.class);
        if (!Objects.equals(storedProject, requestedProject)
                || !Objects.equals(storedTenant, requestedTenant)) {
            throw new IllegalStateException(
                    "CROSS_OWNERSHIP_REVISION_SEMANTIC_CONTEXT_ATTACHMENT_FORBIDDEN_V1: revctx '"
                            + id + "' already bound to (" + storedProject + ", " + storedTenant
                            + ") — requested (" + requestedProject + ", " + requestedTenant
                            + ") FAILS CLOSED (RCOWN3)");
        }
        TimelineRevisionSemanticContext existing;
        try {
            // codec recomputes the revision semantic digest and requires the
            // Effect reference — malformed / tampered rows FAIL CLOSED
            existing = TimelineRevisionSemanticContextJsonCodec.deserialize(storedPayload);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "CORRUPT_REVISION_SEMANTIC_CONTEXT_FAILS_CLOSED_V1: revctx '" + id
                            + "' cannot be decoded — refusing idempotent success: " + e.getMessage(), e);
        }
        if (existing.effectReference() == null
                || incoming.effectReference() == null) {
            throw new IllegalStateException(
                    "CORRUPT_REVISION_SEMANTIC_CONTEXT_FAILS_CLOSED_V1: revctx '" + id
                            + "' lacks the required Effect reference (RCOWN6)");
        }
        // compare the COMPLETE semantic commitment — not just the digest
        boolean sameTimeline = Objects.equals(
                existing.timelineContentDigest(), incoming.timelineContentDigest());
        boolean samePin = Objects.equals(existing.effectReference(),
                incoming.effectReference());
        boolean sameDigest = Objects.equals(
                existing.revisionSemanticDigest(), incoming.revisionSemanticDigest());
        boolean sameContract = Objects.equals(
                existing.digestContractVersion(), incoming.digestContractVersion());
        if (!(sameTimeline && samePin && sameDigest && sameContract)) {
            throw new IllegalArgumentException(
                    "REVISION SEMANTIC CONTEXT IMMUTABILITY: revision " + id.substring(ID_PREFIX.length())
                            + " already owns a different semantic context — historical pin "
                            + "mutation FAILS CLOSED (RCOWN5)");
        }
    }

    @Override
    public Optional<TimelineRevisionSemanticContext> findByRevisionId(
            DSLContext readDsl, String projectId, String tenantId, String revisionId) {
        Objects.requireNonNull(readDsl, "readDsl");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(revisionId, "revisionId");
        String payload = readDsl.fetch(
                        "select payload_json from timeline_snapshot "
                                + "where id = ? and project_id = ? and tenant_id = ?",
                        ID_PREFIX + revisionId, projectId, tenantId)
                .getValues(0, String.class).stream().findFirst().orElse(null);
        if (payload == null) {
            return Optional.empty();
        }
        return Optional.of(TimelineRevisionSemanticContextJsonCodec.deserialize(payload));
    }
}
