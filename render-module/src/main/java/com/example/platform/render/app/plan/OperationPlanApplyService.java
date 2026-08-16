package com.example.platform.render.app.plan;

import com.example.platform.operation.plan.ApplyContext;
import com.example.platform.operation.plan.ApplyResult;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.PlanErrorCode;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.operation.plan.TargetRevisionRef;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (§26/OPI1/OPI3): bounded apply pipeline
 * inside ONE database transaction:
 *
 *   verify plan digest / authorization binding
 *   verify authorization context == apply context
 *   durable ApplyCommandId idempotency (unique key; fingerprint mismatch fails)
 *   database-enforced target-head CAS (UPDATE ... WHERE head = expected; rows==1)
 *   (non-NO_OP) insert immutable revision with parent = plan.baseRevisionId
 *   advance head via the CAS result
 *   persist durable ApplyResult; commit
 *
 * CAS is DATABASE-ENFORCED (conditional UPDATE affected-row semantics), never
 * Java check-then-act: two concurrent writers with the same expected head can
 * never both succeed (exactly one row matches the WHERE clause).
 *
 * NO_OP (candidate hash == base hash): no revision, no head change, durable
 * NO_OP result committed.
 */
public class OperationPlanApplyService {

    private static final Logger log = LoggerFactory.getLogger(OperationPlanApplyService.class);

    private final DSLContext dsl;

    public OperationPlanApplyService(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    public static String fingerprint(String planDigest, TargetRevisionRef ref, String expectedHead,
                                     String projectId, String principalRef) {
        // OPC2: bind principal identity so a durable command cannot be replayed across a
        // different principal / authorization context. Policy version is intentionally
        // EXCLUDED: a completed command must remain replayable as its original historical
        // result even if policy changes later.
        return sha256(planDigest + "|" + projectId + "|" + ref.refId() + "|" + expectedHead + "|" + principalRef);
    }

    public ApplyResult apply(OperationPlan plan, ApplyContext context, String projectId) {
        // explicit jOOQ transaction: CAS + revision insert + idempotency record commit
        // together (works without Spring proxy; single-DB physical atomicity)
        var holder = new java.util.concurrent.atomic.AtomicReference<ApplyResult>();
        dsl.transaction(tx -> {
            holder.set(applyInTransaction(tx.dsl(), plan, context, projectId));
        });
        return holder.get();
    }

    private ApplyResult applyInTransaction(DSLContext tx, OperationPlan plan, ApplyContext context, String projectId) {
        // 1. authorization binds exact plan digest
        if (!context.authorization().planDigest().equals(plan.planDigest())) {
            throw new PlanException(PlanErrorCode.PLAN_CHANGED,
                    "authorization digest " + context.authorization().planDigest()
                            + " != plan digest " + plan.planDigest());
        }
        // 2. authorization binds exact apply context (project + target ref + principal)
        if (!context.authorization().projectId().equals(projectId)
                || !context.authorization().targetRefId().equals(context.targetRef().refId())
                || !context.authorization().principalRef().equals(context.principalRef())) {
            throw new PlanException(PlanErrorCode.AUTHORIZATION_CONTEXT_MISMATCH,
                    "authorization context does not match apply context");
        }
        if (!context.authorization().allowed()) {
            throw new PlanException(PlanErrorCode.AUTHORIZATION_DENIED, "authorization denied");
        }
        // 3. durable idempotency (unique ApplyCommandId; fingerprint mismatch fails)
        String fp = fingerprint(plan.planDigest(), context.targetRef(), context.expectedHeadRevisionId(),
                projectId, context.principalRef());
        var existing = findCommand(tx, context.applyCommandId());
        if (existing != null) {
            if (!existing.fingerprint().equals(fp)) {
                throw new PlanException(PlanErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                        "ApplyCommandId reused with different fingerprint");
            }
            return existing.toResult();
        }
        // 4. reserve command row (status IN_PROGRESS) — same transaction
        insertCommand(tx, context.applyCommandId(), plan.planDigest(), fp, "IN_PROGRESS", projectId);
        if (plan.noOp()) {
            // 5b. NO_OP first execution: validate exact expected head (OPC1) — head unchanged
            int casRows = casHead(tx, projectId, context.targetRef().refId(),
                    context.expectedHeadRevisionId(), null);
            if (casRows != 1) {
                throw new PlanException(PlanErrorCode.STALE_TARGET_REF,
                        "expected head " + context.expectedHeadRevisionId() + " no longer current for ref "
                                + context.targetRef().refId() + " (no-op still requires exact head)");
            }
            updateCommand(tx, context.applyCommandId(), "COMPLETED", null, plan.baseContentHash(),
                    ApplyResult.NO_OP, projectId);
            return ApplyResult.noOp(plan.planDigest(), context.applyCommandId(), plan.baseRevisionId(),
                    plan.baseContentHash(), context.targetRef().refId());
        }
        // 5. database-enforced head CAS with the new head FIRST (conditional update;
        //    rows==1 required; loser's transaction rolls back before any insert, so no
        //    revision_number uniqueness race)
        String newRevisionId = newRevisionId();
        int casRows = casHead(tx, projectId, context.targetRef().refId(),
                context.expectedHeadRevisionId(), newRevisionId);
        if (casRows != 1) {
            throw new PlanException(PlanErrorCode.STALE_TARGET_REF,
                    "expected head " + context.expectedHeadRevisionId() + " no longer current for ref "
                            + context.targetRef().refId());
        }
        // 6. insert immutable revision (parent = plan.baseRevisionId exactly); failure rolls back CAS
        insertRevision(tx, plan, projectId, context, newRevisionId);
        // 7. durable result
        updateCommand(tx, context.applyCommandId(), "COMPLETED", newRevisionId, plan.candidateContentHash(),
                ApplyResult.APPLIED, projectId);
        ApplyResult result = ApplyResult.applied(plan.planDigest(), context.applyCommandId(),
                plan.baseRevisionId(), newRevisionId, plan.candidateContentHash(),
                plan.baseRevisionId(), context.targetRef().refId());
        log.info("Applied plan digest={} revision={} project={}", plan.planDigest(), newRevisionId, projectId);
        return result;
    }

    /**
     * Database-enforced expected-head validation (OPI1 + OPC1).
     * Normal apply: conditional UPDATE advances head; affected rows == 1 => ADVANCED,
     * 0 => STALE_TARGET_REF.
     * NO_OP first execution (OPC1): same conditional UPDATE but WITHOUT advancing the
     * head (version bump only). A no-op relative to R100 is NOT a no-op relative to a
     * moved current head R101 — the exact expected head must still match, else
     * STALE_TARGET_REF. Completed durable replays never reach this path (they return
     * the original durable result before the transaction begins).
     */
    private int casHead(DSLContext tx, String projectId, String refId, String expectedHead,
                         String newRevisionId) {
        if (newRevisionId == null) {
            // NO_OP first execution: validate exact expected head WITHOUT advancing (OPC1)
            return tx.execute("""
                    update timeline_revision_ref
                    set version = version + 1, updated_at = current_timestamp
                    where project_id = ? and ref_id = ? and head_revision_id = ?
                    """, projectId, refId, expectedHead);
        }
        return tx.execute("""
                update timeline_revision_ref
                set head_revision_id = ?, version = version + 1, updated_at = current_timestamp
                where project_id = ? and ref_id = ? and head_revision_id = ?
                """, newRevisionId, projectId, refId, expectedHead);
    }

    private String newRevisionId() {
        return "trev" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private void insertRevision(DSLContext tx, OperationPlan plan, String projectId, ApplyContext context,
                                String revisionId) {
        // authoritative canonical payload: candidate Timeline canonical serialization
        String canonicalJson = serializeCanonical(plan.candidateTimeline());
        long revisionNumber = allocateRevisionNumber(tx, projectId);
        int rows = tx.execute("""
                insert into timeline_revision
                    (id, project_id, tenant_id, parent_revision_id, revision_number, snapshot_id,
                     internal_revision, content_hash, schema_version, source, author_user_id,
                     edit_session_id, message, change_summary_json, created_at, patch_ops_json,
                     labels_json, is_merge)
                values (?, ?, ?, ?, ?, ?, 0, ?, 'internal-1.0', 'operation-plan', ?, null, ?,
                        null, current_timestamp, null, null, false)
                """, revisionId, projectId, null, plan.baseRevisionId(), revisionNumber,
                snapshotId(projectId, canonicalJson), plan.candidateContentHash(),
                context.principalRef(), "operation-plan-apply");
        if (rows != 1) {
            throw new PlanException(PlanErrorCode.PERSISTENCE_FAILURE, "revision insert failed");
        }
        // RCI3: ordered parent edge is the single graph authority (order 0)
        tx.execute("""
                insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order)
                values (?, ?, ?, 0)
                """, projectId, revisionId, plan.baseRevisionId());
    }

    private String snapshotId(String projectId, String canonicalJson) {
        // bounded: single-row snapshot table reuse — keep simple, derive id from hash
        return "snap_" + sha256(projectId + canonicalJson).substring(0, 24);
    }

    /** RCI2: atomic project-scoped allocation (UPDATE ... RETURNING), never MAX+1. */
    private static long allocateRevisionNumber(DSLContext tx, String projectId) {
        var rec = tx.fetchOne("update project_revision_counter set next_revision_number = "
                + "next_revision_number + 1 where project_id = ? returning next_revision_number", projectId);
        if (rec == null) {
            tx.execute("insert into project_revision_counter (project_id, next_revision_number) values (?, 2) "
                    + "on conflict (project_id) do nothing", projectId);
            rec = tx.fetchOne("update project_revision_counter set next_revision_number = "
                    + "next_revision_number + 1 where project_id = ? returning next_revision_number", projectId);
        }
        return rec.get(0, Long.class);
    }

    private String serializeCanonical(com.example.platform.timeline.canonical.TimelineDocument doc) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(doc);
        } catch (Exception e) {
            throw new PlanException(PlanErrorCode.CANONICAL_INVARIANT_VIOLATION, "canonical serialization failed");
        }
    }

    // ---- durable command record ----
    private CommandRecord findCommand(DSLContext tx, String applyCommandId) {
        var rec = tx.fetchOne("select plan_digest, fingerprint, result_revision_id, result_content_hash, result_status "
                + "from apply_command where apply_command_id = ?", applyCommandId);
        if (rec == null) {
            return null;
        }
        return new CommandRecord(rec.get(0, String.class), rec.get(1, String.class),
                rec.get(2, String.class), rec.get(3, String.class), rec.get(4, String.class));
    }

    private void insertCommand(DSLContext tx, String id, String planDigest, String fingerprint, String status, String projectId) {
        tx.execute("""
                insert into apply_command (apply_command_id, plan_digest, fingerprint, status,
                    project_id, command_domain)
                values (?, ?, ?, ?, ?, 'OPERATION_PLAN')
                """, id, planDigest, fingerprint, status, projectId);
    }

    private void updateCommand(DSLContext tx, String id, String status, String resultRevisionId, String resultHash,
                               String resultStatus, String projectId) {
        tx.execute("""
                update apply_command set status = ?, result_revision_id = ?, result_content_hash = ?,
                    result_status = ?, completed_at = current_timestamp
                where apply_command_id = ?
                """, status, resultRevisionId, resultHash, resultStatus, id);
    }

    private record CommandRecord(String planDigest, String fingerprint, String revisionId,
                                  String contentHash, String resultStatus) {
        ApplyResult toResult() {
            if (ApplyResult.NO_OP.equals(resultStatus)) {
                return new ApplyResult(ApplyResult.NO_OP, planDigest, null, null, null,
                        contentHash, null, null, null);
            }
            return new ApplyResult(ApplyResult.APPLIED, planDigest, null, null, revisionId,
                    contentHash, null, null, null);
        }
    }

    private static String sha256(String input) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
