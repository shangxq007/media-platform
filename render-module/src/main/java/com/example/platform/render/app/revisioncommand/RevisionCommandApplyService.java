package com.example.platform.render.app.revisioncommand;

import com.example.platform.render.domain.revisioncommand.RevisionCommandErrorCode;
import com.example.platform.render.domain.revisioncommand.RevisionCommandException;
import com.example.platform.render.domain.revisioncommand.RevisionCommandPlan;
import org.jooq.DSLContext;

import java.time.OffsetDateTime;

/**
 * REVISION_COMMAND_MODEL_V1 (RC12/§20/§38-41): authoritative apply transactions
 * for CREATE_REF / DELETE_REF / RESTORE_REVISION_STATE / MERGE_REVISIONS.
 * One explicit jOOQ transaction each; durable ApplyCommandId idempotency with
 * command-domain separation (RCI domain); DB-enforced expected-head CAS;
 * revision-number via atomic counter (RCI2); ordered parent edges only (RCI3).
 */
public class RevisionCommandApplyService {

    private final DSLContext dsl;

    public RevisionCommandApplyService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public static String fingerprint(String commandDomain, String planDigest, String projectId,
                                     String refContext, String principal) {
        return sha256(commandDomain + "|" + planDigest + "|" + projectId + "|" + refContext + "|" + principal);
    }

    /** CREATE_REF: create ref if absent; never overwrite; no revision created. */
    public String createRef(RevisionCommandPlan.CreateRefPlan plan, String applyCommandId,
                            String principal, String projectId) {
        return dsl.transactionResult(tx -> {
            String domain = "REVISION_COMMAND";
            String fp = fingerprint(domain, plan.planDigest(), projectId, plan.newRef().refId(), principal);
            var existing = tx.dsl().fetchOne("select plan_digest from apply_command where apply_command_id = ?",
                    applyCommandId);
            if (existing != null) {
                if (!existing.get(0, String.class).equals(plan.planDigest())) {
                    throw new RevisionCommandException(RevisionCommandErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                            "ApplyCommandId reused with different command plan");
                }
                return "CREATED";
            }
            tx.dsl().execute("insert into apply_command (apply_command_id, plan_digest, fingerprint, status, project_id, command_domain) "
                    + "values (?, ?, ?, 'COMPLETED', ?, 'REVISION_COMMAND')", applyCommandId, plan.planDigest(), fp, projectId);
            Integer src = tx.dsl().fetchOne("select count(*) from timeline_revision where id = ? and project_id = ?",
                    plan.sourceRevisionId(), plan.newRef().projectId()).get(0, Integer.class);
            if (src == null || src == 0) {
                throw new RevisionCommandException(RevisionCommandErrorCode.REVISION_NOT_FOUND,
                        "source revision not found: " + plan.sourceRevisionId());
            }
            int rows = tx.dsl().execute("insert into timeline_revision_ref (project_id, ref_id, head_revision_id, version) "
                            + "values (?, ?, ?, 0) on conflict (project_id, ref_id) do nothing",
                    plan.newRef().projectId(), plan.newRef().refId(), plan.sourceRevisionId());
            if (rows == 0) {
                throw new RevisionCommandException(RevisionCommandErrorCode.REF_ALREADY_EXISTS,
                        "ref already exists: " + plan.newRef().refId());
            }
            return "CREATED";
        });
    }

    /** DELETE_REF: conditional delete with expected head; history untouched. */
    public String deleteRef(RevisionCommandPlan.DeleteRefPlan plan, String applyCommandId,
                            String principal, String projectId) {
        return dsl.transactionResult(tx -> {
            String domain = "REVISION_COMMAND";
            String fp = fingerprint(domain, plan.planDigest(), projectId, plan.ref().refId(), principal);
            var existing = tx.dsl().fetchOne("select plan_digest from apply_command where apply_command_id = ?",
                    applyCommandId);
            if (existing != null) {
                if (!existing.get(0, String.class).equals(plan.planDigest())) {
                    throw new RevisionCommandException(RevisionCommandErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                            "ApplyCommandId reused with different command plan");
                }
                return "DELETED";
            }
            int rows = tx.dsl().execute("delete from timeline_revision_ref "
                            + "where project_id = ? and ref_id = ? and head_revision_id = ?",
                    plan.ref().projectId(), plan.ref().refId(), plan.expectedHeadRevisionId());
            if (rows == 0) {
                var ref = tx.dsl().fetchOne("select 1 from timeline_revision_ref where project_id = ? and ref_id = ?",
                        plan.ref().projectId(), plan.ref().refId());
                if (ref == null) {
                    throw new RevisionCommandException(RevisionCommandErrorCode.REF_NOT_FOUND,
                            "ref not found: " + plan.ref().refId());
                }
                throw new RevisionCommandException(RevisionCommandErrorCode.STALE_TARGET_REF,
                        "ref head moved: " + plan.ref().refId());
            }
            tx.dsl().execute("insert into apply_command (apply_command_id, plan_digest, fingerprint, status, project_id, command_domain) "
                    + "values (?, ?, ?, 'COMPLETED', ?, 'REVISION_COMMAND')", applyCommandId, plan.planDigest(), fp, projectId);
            return "DELETED";
        });
    }

    /**
     * RESTORE: new single-parent revision (parent order 0 = expected head) with
     * historical canonical payload; CAS target head; NO_OP when candidate hash
     * equals head content hash (no revision, head unchanged, still validates
     * expected head — first execution only; completed replays return original).
     */
    public String restore(RevisionCommandPlan.RestoreRevisionPlan plan, String applyCommandId,
                          String principal, String projectId) {
        return dsl.transactionResult(tx -> {
            String domain = "REVISION_COMMAND";
            String fp = fingerprint(domain, plan.planDigest(), projectId, plan.targetRef().refId(), principal);
            var existing = tx.dsl().fetchOne("select plan_digest from apply_command where apply_command_id = ?",
                    applyCommandId);
            if (existing != null) {
                if (!existing.get(0, String.class).equals(plan.planDigest())) {
                    throw new RevisionCommandException(RevisionCommandErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                            "ApplyCommandId reused with different command plan");
                }
                return existing.get(1, String.class) != null
                        ? "APPLIED:" + existing.get(1, String.class) : "NO_OP";
            }
            // expected-head CAS (version bump only for no-op validation)
            int cas = tx.dsl().execute("update timeline_revision_ref set version = version + 1, "
                            + "updated_at = current_timestamp where project_id = ? and ref_id = ? "
                            + "and head_revision_id = ?",
                    projectId, plan.targetRef().refId(), plan.expectedTargetHeadRevisionId());
            if (cas != 1) {
                throw new RevisionCommandException(RevisionCommandErrorCode.STALE_TARGET_REF,
                        "target head moved: " + plan.targetRef().refId());
            }
            // NO_OP: candidate hash == head content hash -> no revision, head unchanged
            String headHash = tx.dsl().fetchOne("select content_hash from timeline_revision where id = ?",
                    plan.expectedTargetHeadRevisionId()).get(0, String.class);
            if (headHash != null && headHash.equals(plan.candidateContentHash())) {
                tx.dsl().execute("insert into apply_command (apply_command_id, plan_digest, fingerprint, status, "
                                + "result_status, project_id) values (?, ?, ?, 'COMPLETED', 'NO_OP', ?)",
                        applyCommandId, plan.planDigest(), fp, projectId);
                return "NO_OP";
            }
            String revisionId = newRevisionId();
            long number = allocateRevisionNumber(tx.dsl(), projectId);
            String sourcePayload = tx.dsl().fetchOne(
                    "select s.payload from timeline_snapshot s join timeline_revision r on r.snapshot_id = s.id "
                            + "where r.id = ? and r.project_id = ?", plan.historicalSourceRevisionId(), projectId)
                    .get(0, String.class);
            String snapshotId = "snap_" + sha256(projectId + sourcePayload).substring(0, 24);
            tx.dsl().execute("insert into timeline_snapshot (id, payload) values (?, ?) on conflict (id) do nothing",
                    snapshotId, sourcePayload);
            tx.dsl().execute("insert into timeline_revision (id, project_id, parent_revision_id, revision_number, "
                            + "snapshot_id, internal_revision, content_hash, schema_version, source, created_at) "
                            + "values (?, ?, ?, ?, ?, ?, ?, 'internal-1.0', 'revision-command-restore', current_timestamp)",
                    revisionId, projectId, plan.expectedTargetHeadRevisionId(), number, snapshotId,
                    number, plan.candidateContentHash());
            tx.dsl().execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) "
                    + "values (?, ?, ?, 0)", projectId, revisionId, plan.expectedTargetHeadRevisionId());
            tx.dsl().execute("update timeline_revision_ref set head_revision_id = ?, version = version + 1, "
                            + "updated_at = current_timestamp where project_id = ? and ref_id = ? "
                            + "and head_revision_id = ?",
                    revisionId, projectId, plan.targetRef().refId(), plan.expectedTargetHeadRevisionId());
            tx.dsl().execute("insert into apply_command (apply_command_id, plan_digest, fingerprint, status, "
                            + "result_revision_id, result_status, project_id, command_domain) "
                            + "values (?, ?, ?, 'COMPLETED', ?, 'APPLIED', ?, 'REVISION_COMMAND')",
                    applyCommandId, plan.planDigest(), fp, revisionId, projectId);
            return "APPLIED:" + revisionId;
        });
    }

    /**
     * MERGE: two ordered parent edges (0 = target/ours, 1 = source/theirs),
     * explicit merge revision even when target is ancestor of source
     * (NO_IMPLICIT_FAST_FORWARD); conflict => MERGE_CONFLICT (no revision);
     * candidate == target content => NO_OP.
     */
    public String merge(RevisionCommandPlan.MergeRevisionPlan plan, String applyCommandId,
                        String principal, String projectId) {
        return dsl.transactionResult(tx -> {
            String domain = "REVISION_COMMAND";
            String fp = fingerprint(domain, plan.planDigest(), projectId, plan.targetRef().refId(), principal);
            var existing = tx.dsl().fetchOne("select plan_digest from apply_command where apply_command_id = ?",
                    applyCommandId);
            if (existing != null) {
                if (!existing.get(0, String.class).equals(plan.planDigest())) {
                    throw new RevisionCommandException(RevisionCommandErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                            "ApplyCommandId reused with different command plan");
                }
                return existing.get(1, String.class) != null
                        ? "APPLIED:" + existing.get(1, String.class) : "NO_OP";
            }
            if (plan.conflict()) {
                throw new RevisionCommandException(RevisionCommandErrorCode.MERGE_CONFLICT,
                        "merge plan has unresolved semantic conflicts");
            }
            // expected-head CAS (version bump for validation; head update below)
            int cas = tx.dsl().execute("update timeline_revision_ref set version = version + 1, "
                            + "updated_at = current_timestamp where project_id = ? and ref_id = ? "
                            + "and head_revision_id = ?",
                    projectId, plan.targetRef().refId(), plan.targetOursRevisionId());
            if (cas != 1) {
                throw new RevisionCommandException(RevisionCommandErrorCode.STALE_TARGET_REF,
                        "target head moved: " + plan.targetRef().refId());
            }
            String targetHash = tx.dsl().fetchOne("select content_hash from timeline_revision where id = ?",
                    plan.targetOursRevisionId()).get(0, String.class);
            if (targetHash != null && targetHash.equals(plan.candidateContentHash())) {
                tx.dsl().execute("insert into apply_command (apply_command_id, plan_digest, fingerprint, status, "
                                + "result_status, project_id) values (?, ?, ?, 'COMPLETED', 'NO_OP', ?)",
                        applyCommandId, plan.planDigest(), fp, projectId);
                return "NO_OP";
            }
            String revisionId = newRevisionId();
            long number = allocateRevisionNumber(tx.dsl(), projectId);
            String mergedPayload = plan.mergedPayloadJson();
            String snapshotId = "snap_" + sha256(projectId + mergedPayload).substring(0, 24);
            tx.dsl().execute("insert into timeline_snapshot (id, payload) values (?, ?) on conflict (id) do nothing",
                    snapshotId, mergedPayload);
            tx.dsl().execute("insert into timeline_revision (id, project_id, parent_revision_id, revision_number, "
                            + "snapshot_id, internal_revision, content_hash, schema_version, source, created_at, is_merge) "
                            + "values (?, ?, ?, ?, ?, ?, ?, 'internal-1.0', 'revision-command-merge', current_timestamp, true)",
                    revisionId, projectId, plan.targetOursRevisionId(), number, snapshotId,
                    number, plan.candidateContentHash());
            tx.dsl().execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) "
                            + "values (?, ?, ?, 0)", projectId, revisionId, plan.targetOursRevisionId());
            tx.dsl().execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) "
                            + "values (?, ?, ?, 1)", projectId, revisionId, plan.sourceRevisionId());
            tx.dsl().execute("update timeline_revision_ref set head_revision_id = ?, version = version + 1, "
                            + "updated_at = current_timestamp where project_id = ? and ref_id = ? "
                            + "and head_revision_id = ?",
                    revisionId, projectId, plan.targetRef().refId(), plan.targetOursRevisionId());
            tx.dsl().execute("insert into apply_command (apply_command_id, plan_digest, fingerprint, status, "
                            + "result_revision_id, result_status, project_id, command_domain) "
                            + "values (?, ?, ?, 'COMPLETED', ?, 'APPLIED', ?, 'REVISION_COMMAND')",
                    applyCommandId, plan.planDigest(), fp, revisionId, projectId);
            return "APPLIED:" + revisionId;
        });
    }

    private static long allocateRevisionNumber(org.jooq.DSLContext tx, String projectId) {
        // RCI2: atomic project-scoped allocation (UPDATE ... RETURNING), never MAX+1
        Long next = tx.fetchOne("update project_revision_counter set next_revision_number = "
                        + "next_revision_number + 1 where project_id = ? returning next_revision_number",
                projectId).get(0, Long.class);
        if (next == null) {
            tx.execute("insert into project_revision_counter (project_id, next_revision_number) values (?, 2) "
                    + "on conflict (project_id) do nothing", projectId);
            next = tx.fetchOne("update project_revision_counter set next_revision_number = "
                    + "next_revision_number + 1 where project_id = ? returning next_revision_number",
                    projectId).get(0, Long.class);
        }
        return next;
    }

    private static String newRevisionId() {
        return "trev" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
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
